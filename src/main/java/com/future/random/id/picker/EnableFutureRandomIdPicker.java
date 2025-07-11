package com.future.random.id.picker;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(RandomIdPickerConfiguration.class)
public @interface EnableFutureRandomIdPicker {

}
