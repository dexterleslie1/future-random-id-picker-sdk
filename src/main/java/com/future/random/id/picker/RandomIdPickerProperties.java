package com.future.random.id.picker;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.future.random.id.picker")
@Data
public class RandomIdPickerProperties {
    private String host = "localhost";
    private int port = 8080;
    private int cacheSize = 1024;
}
