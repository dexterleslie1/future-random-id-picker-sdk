package com.future.random.id.picker;


import com.future.common.exception.BusinessException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.annotation.Resource;

@EnableConfigurationProperties(RandomIdPickerProperties.class)
public class RandomIdPickerConfiguration {
    @Resource
    RandomIdPickerProperties randomIdPickerProperties;

    @Bean(initMethod = "start", destroyMethod = "close")
    public RandomIdPickerService randomIdPickerService() throws BusinessException {
        return new RandomIdPickerService(
                randomIdPickerProperties.getHost(),
                randomIdPickerProperties.getPort(),
                randomIdPickerProperties.getCacheSize(),
                randomIdPickerProperties.getFlagList()
        );
    }
}
