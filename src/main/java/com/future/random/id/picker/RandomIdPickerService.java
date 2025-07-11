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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RandomIdPickerService {

    private ScheduledExecutorService executorService;
    private Map<String, List<Long>> flagToIdListMap = new HashMap<>();
    private int cacheSize;
    private Api api;

    /**
     * @param host
     * @param port
     * @param cacheSize 缓存中id列表的总数
     */
    public RandomIdPickerService(String host, int port, int cacheSize) {
        if (cacheSize <= 0) {
            cacheSize = 1024;
        }
        this.cacheSize = cacheSize;

        api = Feign.builder()
                // https://stackoverflow.com/questions/56987701/feign-client-retry-on-exception
                .retryer(Retryer.NEVER_RETRY)
                // https://qsli.github.io/2020/04/28/feign-method-timeout/
                .options(new Request.Options(15, TimeUnit.SECONDS, 15, TimeUnit.SECONDS, false))
                .encoder(new FormEncoder(new JacksonEncoder()))
                .decoder(new JacksonDecoder())
                // feign logger
                // https://cloud.tencent.com/developer/article/1588501
                .logger(new Logger.ErrorLogger()).logLevel(Logger.Level.FULL)
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
                    ListResponse<Long> response = api.listIdRandomly(flag, this.cacheSize);
                    List<Long> idList = response.getData();
                    if (idList != null && !idList.isEmpty()) {
                        flagToIdListMap.put(flag, idList);
                        if (log.isDebugEnabled())
                            log.debug("flag {} 成功更新 id 列表缓存 idList {}", flag, idList);
                    } else {
                        if (log.isDebugEnabled())
                            log.debug("flag {} 尝试从随机 id 服务获取随机 id 列表，但是返回的 id 列表为空", flag);
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

    public void register(String flag) throws BusinessException {
        api.init(flag);
        flagToIdListMap.put(flag, null);
        if (log.isDebugEnabled())
            log.debug("成功注册 flag {}", flag);
    }

    public void addIdList(String flag, List<Long> idList) throws BusinessException {
        api.addIdList(flag, idList);
    }

    public List<Long> listIdRandomly(String flag, int size) throws BusinessException {
        if (!flagToIdListMap.containsKey(flag)) {
            throw new BusinessException("不存在 flag=" + flag + " 的 id 缓存标识，请先调用 register 方法注册");
        }

        List<Long> idList = flagToIdListMap.get(flag);
        if (idList == null || idList.isEmpty()) {
            throw new BusinessException("flag=" + flag + " 的 id 缓存暂时没有 id 列表，请稍后再试");
        }

        int start = RandomUtil.randomInt(0, idList.size());
        int end = start + size;
        if (end > idList.size() - 1)
            end = idList.size() - 1;
        return idList.subList(start, end);
    }
}
