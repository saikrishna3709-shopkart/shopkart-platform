package com.shopkart.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.codehaus.groovy.control.SourceUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

@EnabledIfSystemProperty(named = "shopkart.manifests", matches = ".+")
class DeploymentConfigurationTest {

    private static final List<String> SERVICES = List.of(
            "catalog-service", "inventory-service", "order-service",
            "payment-service", "notification-service", "storefront-service");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final Path manifests = Path.of(System.getProperty("shopkart.manifests"));

    @TempDir
    Path temporaryDirectory;

    @Test
    void applicationSetRendersAllSixNamesAndValuesPaths() throws Exception {
        JsonNode applicationSet = documents(Files.readString(manifests.resolve("deploy/argocd/shopkart-dev.yaml")))
                .stream().filter(document -> "ApplicationSet".equals(document.path("kind").asText()))
                .findFirst().orElseThrow();
        JsonNode spec = applicationSet.path("spec");
        assertTrue(spec.path("goTemplate").asBoolean(), "ApplicationSet must enable Go templates");
        assertTrue(spec.path("goTemplateOptions").toString().contains("missingkey=error"));
        String nameTemplate = spec.at("/template/metadata/name").asText();
        String valueTemplate = spec.at("/template/spec/source/helm/valueFiles/0").asText();
        Path chart = temporaryDirectory.resolve("probe");
        Files.createDirectories(chart.resolve("templates"));
        Files.writeString(chart.resolve("Chart.yaml"), "apiVersion: v2\nname: appset-probe\nversion: 0.1.0\n");
        Files.writeString(chart.resolve("templates/probe.yaml"), """
                apiVersion: v1
                kind: ConfigMap
                metadata:
                  name: appset-probe
                data:
                  name: {{ tpl .Values.nameTemplate (merge (dict "path" .Values.path) .) | quote }}
                  valuesFile: {{ tpl .Values.valueTemplate (merge (dict "path" .Values.path) .) | quote }}
                """);
        for (String service : SERVICES) {
            Path values = temporaryDirectory.resolve("probe-values.yaml");
            Files.writeString(values, new Yaml().dump(Map.of(
                    "nameTemplate", nameTemplate, "valueTemplate", valueTemplate,
                    "path", Map.of("filename", service + ".yaml"))));
            JsonNode data = documents(helm("template", "probe", chart.toString(), "-f", values.toString()))
                    .get(0).path("data");
            assertEquals(service + "-dev", data.path("name").asText());
            Path resolved = manifests.resolve("deploy/charts/shopkart-service")
                    .resolve(data.path("valuesFile").asText()).normalize();
            assertEquals(manifests.resolve("deploy/envs/dev/values/" + service + ".yaml").normalize(), resolved);
            assertTrue(Files.isRegularFile(resolved));
        }
    }

    @Test
    void allHelmReleasesExposeTheImageRevisionAndHaveMatchingSelectors() throws Exception {
        for (String service : SERVICES) {
            List<JsonNode> rendered = documents(helm("template", service + "-dev",
                    manifests.resolve("deploy/charts/shopkart-service").toString(),
                    "-f", manifests.resolve("deploy/envs/dev/values/" + service + ".yaml").toString(),
                    "--namespace", "shopkart-dev", "--set-string", "image.tag=abc123def456",
                    "--set-string", "image.registry=123456789012.dkr.ecr.us-east-1.amazonaws.com"));
            JsonNode deployment = kind(rendered, "Deployment");
            JsonNode container = deployment.at("/spec/template/spec/containers/0");
            assertEquals("123456789012.dkr.ecr.us-east-1.amazonaws.com/shopkart/" + service + ":abc123def456",
                    container.path("image").asText());
            boolean revisionFound = false;
            for (JsonNode variable : container.path("env")) {
                if ("APP_REVISION".equals(variable.path("name").asText())) {
                    assertEquals("abc123def456", variable.path("value").asText());
                    revisionFound = true;
                }
            }
            assertTrue(revisionFound, service + " must report its deployed image tag");
            assertEquals(deployment.at("/spec/selector/matchLabels"), kind(rendered, "Service").at("/spec/selector"));
            assertEquals(deployment.at("/spec/selector/matchLabels"), kind(rendered, "NetworkPolicy").at("/spec/podSelector/matchLabels"));
            assertEquals("/readyz", container.at("/readinessProbe/httpGet/path").asText());
        }
    }

    @Test
    void monitoringHasValidReceiversAndNeedsNoPersistentVolumeProvisioner() throws Exception {
        List<JsonNode> rendered = documents(helm("template", "kube-prometheus-stack", "kube-prometheus-stack",
                "--repo", "https://prometheus-community.github.io/helm-charts", "--version", "82.2.0",
                "--namespace", "monitoring", "-f",
                manifests.resolve("monitoring/kube-prometheus-stack-values.yaml").toString()));
        JsonNode secret = rendered.stream()
                .filter(document -> document.path("data").has("alertmanager.yaml"))
                .findFirst().orElseThrow();
        String config = new String(Base64.getDecoder().decode(secret.path("data").path("alertmanager.yaml").asText()),
                java.nio.charset.StandardCharsets.UTF_8);
        JsonNode alertmanager = documents(config).get(0);
        Set<String> receivers = new HashSet<>();
        for (JsonNode receiver : alertmanager.path("receivers")) {
            receivers.add(receiver.path("name").asText());
        }
        for (JsonNode receiver : alertmanager.path("route").findValues("receiver")) {
            assertTrue(receivers.contains(receiver.asText()), "undefined Alertmanager receiver: " + receiver);
        }
        assertTrue(!config.contains("${"), "Alertmanager must not contain unresolved environment placeholders");
        assertTrue(rendered.stream().noneMatch(document -> "PersistentVolumeClaim".equals(document.path("kind").asText())));
        assertTrue(kind(rendered, "Prometheus").at("/spec/storage").isMissingNode());
        assertEquals("6h", kind(rendered, "Prometheus").at("/spec/retention").asText());
    }

    @Test
    void sharedPipelineParsesAndSuppliesTheExpectedRevision() throws Exception {
        Path library = Path.of(System.getProperty("shopkart.library",
                manifests.resolveSibling("shopkart-jenkins-library").toString()));
        String source = Files.readString(library.resolve("vars/shopkartPipeline.groovy"));
        SourceUnit unit = SourceUnit.create("shopkartPipeline.groovy", source);
        unit.parse();
        unit.completePhase();
        unit.nextPhase();
        unit.convert();
        assertTrue(source.contains("-Dshopkart.expectedRevision=\"${IMAGE_TAG}\""));
        assertTrue(source.contains("tests/smoke/target/surefire-reports/*.xml"));
    }

    private JsonNode kind(List<JsonNode> documents, String kind) {
        return documents.stream().filter(document -> kind.equals(document.path("kind").asText()))
                .findFirst().orElseThrow();
    }

    private List<JsonNode> documents(String source) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        List<JsonNode> result = new ArrayList<>();
        for (Object document : new Yaml(new SafeConstructor(options)).loadAll(source)) {
            if (document != null) {
                result.add(OBJECT_MAPPER.valueToTree(document));
            }
        }
        return result;
    }

    private String helm(String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(System.getProperty("shopkart.helm", "helm"));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        return output;
    }
}