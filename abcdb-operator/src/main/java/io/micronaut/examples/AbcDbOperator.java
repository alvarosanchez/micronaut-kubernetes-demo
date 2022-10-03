package io.micronaut.examples;

import io.dekorate.kubernetes.annotation.ImagePullPolicy;
import io.dekorate.kubernetes.annotation.Protocol;
import io.dekorate.kubernetes.annotation.ServiceType;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.ModelMapper;
import io.micronaut.examples.models.V1AbcDb;
import io.micronaut.examples.models.V1AbcDbList;
import io.micronaut.kubernetes.client.informer.Informer;
import io.micronaut.kubernetes.client.operator.Operator;
import io.micronaut.kubernetes.client.operator.OperatorResourceLister;
import io.micronaut.kubernetes.client.operator.ResourceReconciler;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static io.micronaut.examples.AbcDbOperator.NAME;

@Operator(
        name = NAME,
        informer = @Informer(apiType = V1AbcDb.class, apiListType = V1AbcDbList.class, namespace = V1AbcDb.NAMESPACE, resyncCheckPeriod = 2000L)
)
public class AbcDbOperator implements ResourceReconciler<V1AbcDb> {

    public static final String NAME = "abcdb-operator";
    public static final String FINALIZER = V1AbcDb.GROUP + "/" + NAME;

    private static final Logger LOG = LoggerFactory.getLogger(AbcDbOperator.class);
    public static final String CONFIG_YML = "config.yml";

    private final CustomObjectsApi customApi;
    private final CoreV1Api coreApi;
    private final AppsV1Api appsApi;

    public AbcDbOperator(CustomObjectsApi customApi, CoreV1Api coreApi, AppsV1Api appsApi) {
        this.customApi = customApi;
        this.coreApi = coreApi;
        this.appsApi = appsApi;
        ModelMapper.addModelMap(V1AbcDb.GROUP, V1AbcDb.API_VERSION, V1AbcDb.KIND, V1AbcDb.PLURAL, V1AbcDb.class, V1AbcDbList.class);
    }

    @NotNull
    @Override
    public Result reconcile(@NotNull Request request, OperatorResourceLister<V1AbcDb> lister) {
        Optional<V1AbcDb> abcDbOptional = lister.get(request);
        if (abcDbOptional.isPresent()) {
            V1AbcDb abcDb = abcDbOptional.get();

            if (abcDb.isBeingDeleted()) {
                return deleteObjects(abcDb);
            } else {
                return createOrUpdateObjects(abcDb);
            }
        }
        return new Result(false);
    }

    @NotNull
    private Result createOrUpdateObjects(V1AbcDb abcDb) {
        try {
            int operations = 0;
            operations += createOrUpdateConfigMap(abcDb);
            operations += createDeploymentIfNeeded(abcDb);
            operations += createServiceIfNeeded(abcDb);
            if (operations > 0) {
                return process(abcDb);
            } else {
                return new Result(false);
            }
        } catch (ApiException e) {
            LOG.error("Failed to create objects {}", e.getResponseBody(), e);
            return new Result(true);
        }
    }

    private int createOrUpdateConfigMap(V1AbcDb abcDb) throws ApiException {
        V1ConfigMapList configMapList = coreApi.listNamespacedConfigMap(V1AbcDb.NAMESPACE, null, null, null, null, null, null, null, null, null, null);
        V1ConfigMap configMap;
        if (configMapList.getItems().stream().noneMatch(cm -> cm.getMetadata().getName().equals(abcDb.getName()))) {
            configMap = new V1ConfigMapBuilder()
                    .withNewMetadata()
                    .withName(abcDb.getName())
                    .withLabels(commonLabels(abcDb))
                    .addToOwnerReferences(ownerReference(abcDb))
                    .endMetadata()
                    .withData(Map.of(CONFIG_YML, configMapMessage(abcDb)))
                    .build();
            coreApi.createNamespacedConfigMap(V1AbcDb.NAMESPACE, configMap, null, null, null);
            LOG.info("Created configMap {}", configMap.getMetadata().getName());
            return 1;
        } else {
            configMap = coreApi.readNamespacedConfigMap(abcDb.getName(), V1AbcDb.NAMESPACE, null, null, null);
            String existingMessage = configMap.getData().get(CONFIG_YML);
            if (!existingMessage.equals(configMapMessage(abcDb))) {
                configMap.setData(Map.of(CONFIG_YML, configMapMessage(abcDb)));
                coreApi.replaceNamespacedConfigMap(abcDb.getName(), V1AbcDb.NAMESPACE, configMap, null, null, null);
                LOG.info("Replaced configMap {}", configMap.getMetadata().getName());
                return 1;
            }
        }

        return 0;
    }

    @NotNull
    private static String configMapMessage(V1AbcDb abcDb) {
        return "abcdb.message: " + abcDb.getMessage();
    }

    @NotNull
    private Result deleteObjects(V1AbcDb abcDb) {
        deleteConfigMap(abcDb);
        deleteService(abcDb);
        deleteDeployment(abcDb);
        return finalizeDeletion(abcDb);
    }

    private void deleteConfigMap(V1AbcDb abcDb) {
        try {
            coreApi.deleteNamespacedConfigMap(abcDb.getName(), V1AbcDb.NAMESPACE, null, null,  null, null, null, null);
        } catch (ApiException e) {
            LOG.error("Failed to delete configMap {}", e.getResponseBody(), e);
        }
    }

    private Result finalizeDeletion(V1AbcDb abcDb) {
        try {
            abcDb.finalizeDeletion();
            customApi.replaceNamespacedCustomObject(V1AbcDb.GROUP, V1AbcDb.API_VERSION, V1AbcDb.NAMESPACE, V1AbcDb.PLURAL, abcDb.getName(), abcDb, null, null);
            return new Result(false);
        } catch (ApiException e) {
            LOG.error("Failed to update abcdb", e);
            return new Result(true, Duration.ofSeconds(2));
        }
    }

    private void deleteDeployment(V1AbcDb abcDb) {
        try {
            appsApi.deleteNamespacedDeployment(abcDb.getName(), V1AbcDb.NAMESPACE, null, null,  null, null, null, null);
        } catch (ApiException e) {
            LOG.error("Failed to delete deployment {}", e.getResponseBody(), e);
        }
    }

    private void deleteService(V1AbcDb abcDb) {
        try {
            coreApi.deleteNamespacedService(abcDb.getName(), V1AbcDb.NAMESPACE, null, null,  null, null, null, null);
        } catch (ApiException e) {
            LOG.error("Failed to delete service {}", e.getResponseBody(), e);
        }
    }

    private int createServiceIfNeeded(V1AbcDb abcDb) throws ApiException {
        V1ServiceList serviceList = coreApi.listNamespacedService(V1AbcDb.NAMESPACE, null, null, null, null, null, null, null, null, null, null);
        V1Service service;
        if (serviceList.getItems().stream().noneMatch(v1Service -> v1Service.getMetadata().getName().equals(abcDb.getName()))) {
            service = new V1ServiceBuilder()
                    .withNewMetadata()
                    .withName(abcDb.getName())
                    .withLabels(commonLabels(abcDb))
                    .addToOwnerReferences(ownerReference(abcDb))
                    .endMetadata()
                    .withNewSpec()
                    .withType(ServiceType.LoadBalancer.name())
                    .addNewPort()
                    .withName("http")
                    .withPort(8080)
                    .withTargetPort(new IntOrString(8080))
                    .endPort()
                    .withSelector(commonLabels(abcDb))
                    .endSpec()
                    .build();

            coreApi.createNamespacedService(V1AbcDb.NAMESPACE, service, null, null, null);
            LOG.info("Created service {}", service.getMetadata().getName());
            return 1;
        } else {
            return 0;
        }
    }

    private int createDeploymentIfNeeded(V1AbcDb abcDb) throws ApiException {
        V1DeploymentList deploymentList = appsApi.listNamespacedDeployment(V1AbcDb.NAMESPACE, null, null, null, null, null, null, null, null, null, null);
        V1Deployment deployment;
        if (deploymentList.getItems().stream().noneMatch(v1Deployment -> v1Deployment.getMetadata().getName().equals(abcDb.getName()))) {
            deployment = new V1DeploymentBuilder()
                    .withNewMetadata()
                    .withName(abcDb.getName())
                    .withLabels(commonLabels(abcDb))
                    .addToOwnerReferences(ownerReference(abcDb))
                    .endMetadata()
                    .withNewSpec()
                    .withReplicas(1)
                    .withNewSelector()
                    .addToMatchLabels(commonLabels(abcDb))
                    .endSelector()
                    .withNewTemplate()
                    .withNewMetadata()
                    .withLabels(commonLabels(abcDb))
                    .endMetadata()
                    .withNewSpec()
                    .addToContainers(getContainer(abcDb))
                    .endSpec()
                    .endTemplate()
                    .endSpec()
                    .build();

            appsApi.createNamespacedDeployment(V1AbcDb.NAMESPACE, deployment, null, null, null);
            LOG.info("Created deployment {}", deployment.getMetadata().getName());
            return 1;
        } else {
            return 0;
        }
    }

    private V1Container getContainer(V1AbcDb abcDb) {
        return new V1ContainerBuilder()
                .withImage("us-phoenix-1.ocir.io/oraclelabs/abcdb:0.1")
                .withImagePullPolicy(ImagePullPolicy.IfNotPresent.name())
                .withName(abcDb.getName())
                .addNewPort()
                    .withContainerPort(8080)
                    .withName("http")
                    .withProtocol(Protocol.TCP.name())
                .endPort()
                .addNewEnv()
                    .withName("KUBERNETES_NAMESPACE")
                        .withNewValueFrom()
                            .withNewFieldRef()
                                .withFieldPath("metadata.namespace")
                            .endFieldRef()
                        .endValueFrom()
                    .endEnv()
                .build();
    }

    private V1OwnerReference ownerReference(V1AbcDb abcDb) {
        return new V1OwnerReferenceBuilder()
                .withName(abcDb.getName())
                .withApiVersion(V1AbcDb.API_VERSION)
                .withKind(V1AbcDb.KIND)
                .withController(Boolean.TRUE)
                .withUid(abcDb.getMetadata().getUid())
                .withBlockOwnerDeletion(Boolean.TRUE)
                .build();
    }

    private Result process(V1AbcDb abcDb) throws ApiException {
        abcDb.setReconciled();
        customApi.replaceNamespacedCustomObject(V1AbcDb.GROUP, V1AbcDb.API_VERSION, V1AbcDb.NAMESPACE, V1AbcDb.PLURAL, abcDb.getName(), abcDb, null, null);
        return new Result(false);
    }

    private Map<String, String> commonLabels(V1AbcDb abcDb) {
        return Map.of(
                "app.kubernetes.io/name", abcDb.getName(),
                "app", abcDb.getName(),
                "app.kubernetes.io/version", "0.1"
        );
    }

}
