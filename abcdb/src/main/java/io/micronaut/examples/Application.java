package io.micronaut.examples;

import io.dekorate.docker.annotation.DockerBuild;
import io.dekorate.kubernetes.annotation.ImagePullPolicy;
import io.dekorate.kubernetes.annotation.KubernetesApplication;
import io.dekorate.kubernetes.annotation.Label;
import io.dekorate.kubernetes.annotation.Port;
import io.dekorate.kubernetes.annotation.ServiceType;
import io.micronaut.runtime.Micronaut;

import static io.micronaut.examples.Application.APP_NAME;
import static io.micronaut.examples.Application.NAME;

@KubernetesApplication(
    name = NAME,
    labels = @Label(key = "app", value = APP_NAME),
    ports = @Port(name = "http", containerPort = 8080, hostPort = 8080),
    imagePullPolicy = ImagePullPolicy.Always,
    serviceType = ServiceType.LoadBalancer
)
@DockerBuild(group = "alvarosanchez", name = NAME)
public class Application {

    public static final String NAME = "abcdb";
    public static final String APP_NAME = NAME;

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }

}
