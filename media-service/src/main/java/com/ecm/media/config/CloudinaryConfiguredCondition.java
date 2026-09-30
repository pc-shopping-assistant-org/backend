package com.ecm.media.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class CloudinaryConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment environment = context.getEnvironment();
        return hasValue(environment, "cloudinary.cloud-name")
                && hasValue(environment, "cloudinary.api-key")
                && hasValue(environment, "cloudinary.api-secret");
    }

    private boolean hasValue(Environment environment, String property) {
        String value = environment.getProperty(property);
        return value != null && !value.isBlank();
    }
}
