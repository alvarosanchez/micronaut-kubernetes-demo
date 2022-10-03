package io.micronaut.examples;

import io.dekorate.docker.annotation.DockerBuild;
import io.dekorate.kubernetes.annotation.ImagePullPolicy;
import io.dekorate.kubernetes.annotation.ServiceType;
import io.kubernetes.client.util.ModelMapper;
import io.micronaut.examples.models.V1AbcDb;
import io.micronaut.examples.models.V1AbcDbList;
import io.micronaut.runtime.Micronaut;
import io.dekorate.kubernetes.annotation.KubernetesApplication;
import io.dekorate.kubernetes.annotation.Label;
import io.dekorate.kubernetes.annotation.Port;

import static io.micronaut.examples.Application.APP_NAME;
import static io.micronaut.examples.Application.NAME;

@KubernetesApplication(
        name = NAME,
        labels = @Label(key = "app", value = APP_NAME),
        ports = @Port(name = "http", containerPort = 8080),
        imagePullPolicy = ImagePullPolicy.IfNotPresent,
        serviceType = ServiceType.ClusterIP
)
@DockerBuild(registry="us-phoenix-1.ocir.io", group = "oraclelabs", name = NAME)
public class Application {

    public static final String NAME = "abcdb-operator";
    public static final String APP_NAME = "abcdb";

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }


}
