package com.future.random.id.picker;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "spring.future.random.id.picker")
@Data
public class RandomIdPickerProperties {
    private String host = "localhost";
    private int port = 8080;
    private int cacheSize = 1024;
    /*@NotEmpty(message = "请指定服务支持的flag列表")*/
    private List<String> flagList;
}
