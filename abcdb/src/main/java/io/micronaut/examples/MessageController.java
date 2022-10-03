package io.micronaut.examples;

import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.Environment;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.runtime.context.scope.Refreshable;

@Controller
@Refreshable
public class MessageController {

    @Value("${abcdb.message:Hello, I am an unconfigured database}")
    private String message;

    private final Environment environment;

    public MessageController(Environment environment) {
        this.environment = environment;
    }

    @Get
    public String getMessage() {
        String textToAppend;
        if (environment.getActiveNames().contains(Environment.KUBERNETES)) {
            textToAppend = " running in a Kubernetes cluster";
        } else {
            textToAppend = " running outside Kubernetes";
        }
        return message + textToAppend;
    }
}
