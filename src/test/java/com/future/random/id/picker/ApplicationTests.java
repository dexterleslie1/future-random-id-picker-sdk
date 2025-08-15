package com.future.random.id.picker;

import com.future.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@SpringBootTest(classes = Application.class)
@Slf4j
@EnableFutureRandomIdPicker
public class ApplicationTests {

    @Resource
    RandomIdPickerService randomIdPickerService;

    /**
     * @throws BusinessException
     */
    @Test
    public void contextLoads() throws BusinessException, InterruptedException {
        String flag = "order";
        List<String> idList = new ArrayList<>();
        for (long i = 0; i < 1024; i++)
            idList.add(String.valueOf(1001 + i));
        randomIdPickerService.addIdList(flag, idList);

        // 等待后台线程获取 id 列表到缓存
        TimeUnit.SECONDS.sleep(6);
        idList = randomIdPickerService.listIdRandomly(flag, 10);
        log.debug("成功从本地缓存中随机获取 id 列表 idList {}", idList);
        Assertions.assertEquals(10, idList.size());
    }
}
