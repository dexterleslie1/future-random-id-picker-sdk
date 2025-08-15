package com.future.random.id.picker;

import cn.hutool.core.util.RandomUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.future.common.exception.BusinessException;
import com.future.common.http.ListResponse;
import com.future.common.http.ObjectResponse;
import com.future.common.json.JSONUtil;
import feign.*;
import feign.codec.ErrorDecoder;
import feign.form.FormEncoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.util.Assert;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class RandomIdPickerService {

    private ScheduledExecutorService executorService;
    private Map<String, List<String>> flagToIdListMap;
    private int cacheSize;
    private Api api;

    /**
     * @param host
     * @param port
     * @param cacheSize 缓存中id列表的总数
     * @param flagList  服务支持的flag列表
     */
    public RandomIdPickerService(String host, int port, int cacheSize, List<String> flagList) throws BusinessException {
        Assert.isTrue(flagList != null && !flagList.isEmpty(), "请指定服务支持的flag列表");

        if (cacheSize <= 0) {
            cacheSize = 1024;
        }
        this.cacheSize = cacheSize;
        this.flagToIdListMap = flagList.stream().collect(Collectors.toMap(o -> o, o -> new ArrayList<>()));

        api = Feign.builder()
                // https://stackoverflow.com/questions/56987701/feign-client-retry-on-exception
                .retryer(Retryer.NEVER_RETRY)
                // https://qsli.github.io/2020/04/28/feign-method-timeout/
                .options(new Request.Options(15, TimeUnit.SECONDS, 15, TimeUnit.SECONDS, false))
                .encoder(new FormEncoder(new JacksonEncoder()))
                .decoder(new JacksonDecoder())
                // feign logger
                // https://cloud.tencent.com/developer/article/1588501
                .logger(new Logger.ErrorLogger()).logLevel(Logger.Level.NONE)
                // ErrorDecoder
                // https://cloud.tencent.com/developer/article/1588501
                .errorDecoder(new ErrorDecoder() {
                    @Override
                    public Exception decode(String methodKey, Response response) {
                        try {
                            String json = IOUtils.toString(response.body().asInputStream(), StandardCharsets.UTF_8);
                            ObjectResponse<String> responseError = JSONUtil.ObjectMapperInstance.readValue(json, new TypeReference<ObjectResponse<String>>() {
                            });
                            return new BusinessException(responseError.getErrorCode(), responseError.getErrorMessage());
                        } catch (IOException e) {
                            return e;
                        }
                    }
                })
                .target(Api.class, "http://" + host + ":" + port);

        // 调用随机 id 选择器服务 init 方法以初始化对应的 flag
        boolean exit = false;
        int failCount = 0;
        while (!exit) {
            boolean exception = false;
            for (String flag : RandomIdPickerService.this.flagToIdListMap.keySet()) {
                try {
                    api.init(flag);
                    if (log.isDebugEnabled())
                        log.debug("成功调用随机 id 选择器服务 init 方法，flag {}", flag);
                } catch (Exception ex) {
                    log.error("调用随机 id 选择器服务 init 方法失败，flag {}，原因：{}", flag, ex.getMessage());

                    failCount = failCount + 1;
                    if (failCount >= 10) {
                        throw new BusinessException("尝试调用随机 id 选择器服务的 init 方法共失败 " + failCount + " 次，可能是因为随机 id 选择器服务没有启动并运行");
                    }

                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException ignored) {

                    }

                    exception = true;
                    break;
                }
            }

            if (!exception)
                exit = true;
        }
        if (log.isDebugEnabled())
            log.debug("成功调用随机 id 选择器服务 init 方法，flagList {}", flagToIdListMap.keySet());

    }

    /**
     * 应用启动后需要调用此方法启动服务
     */
    public synchronized void start() {
        if (executorService != null) {
            if (log.isInfoEnabled())
                log.info("服务已经启动");
            return;
        }

        executorService = Executors.newScheduledThreadPool(1);
        executorService.scheduleAtFixedRate(() -> {
            for (String flag : flagToIdListMap.keySet()) {
                try {
                    ListResponse<String> response = api.listIdRandomly(flag, this.cacheSize);
                    List<String> idList = response.getData();
                    if (idList != null && !idList.isEmpty()) {
                        flagToIdListMap.put(flag, idList);
                        if (log.isDebugEnabled())
                            log.debug("flag {} 成功从随机 id 选择器服务随机获取 id 列表并更新到本地缓存 id 列表 id 个数为 {}", flag, idList.size());
                    } else {
                        if (log.isDebugEnabled())
                            log.debug("flag {} 尝试从随机 id 选择器服务获取随机 id 列表但是返回的随机 id 列表为空", flag);
                    }
                } catch (Exception ex) {
                    log.error(ex.getMessage(), ex);
                }
            }
        }, 5, 5, TimeUnit.SECONDS);

        if (log.isDebugEnabled())
            log.debug("成功启动服务");
    }

    /**
     * 应用关闭时需要调用此方法关闭服务
     *
     * @throws Exception
     */
    public synchronized void close() throws Exception {
        if (this.executorService == null) {
            if (log.isInfoEnabled())
                log.info("服务已经关闭");
            return;
        }

        this.executorService.shutdown();
        while (!this.executorService.awaitTermination(100, TimeUnit.MILLISECONDS)) ;
        this.executorService = null;
        if (log.isDebugEnabled())
            log.debug("成功关闭服务");
    }

    public void addIdList(String flag, List<String> idList) throws BusinessException {
        api.addIdList(flag, idList);
    }

    public List<String> listIdRandomly(String flag, int size) throws BusinessException {
        if (!flagToIdListMap.containsKey(flag)) {
            throw new BusinessException("不存在 flag=" + flag + " 的 id 缓存标识，请先在应用中使用 spring.future.random.id.picker.flag-list=" + flag + " 配置再使用");
        }

        List<String> idList = flagToIdListMap.get(flag);
        if (idList == null || idList.isEmpty()) {
            throw new BusinessException("flag=" + flag + " 的 id 缓存暂时没有 id 列表，请稍后再试");
        }

        List<String> randomIdList = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            int randomInt = RandomUtil.randomInt(0, idList.size());
            String randomId = idList.get(randomInt);
            if (!randomIdList.contains(randomId))
                randomIdList.add(randomId);
        }
        return randomIdList;
    }
}
