package com.bidguard;

import com.aliyun.tea.*;
import com.aliyun.ocr_api20210707.models.*;
import com.aliyun.teautil.models.RuntimeOptions;
import com.google.gson.GsonBuilder;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

public class RecognizeCharacter {

    public static com.aliyun.ocr_api20210707.Client createClient() throws Exception {
        // 从配置文件读取阿里云密钥
        SimilarityConfig cfg = SimilarityConfig.getInstance();
        
        com.aliyun.teaopenapi.models.Config config =
                new com.aliyun.teaopenapi.models.Config();
        
        // 从配置文件读取，而不是硬编码
        config.setAccessKeyId(cfg.ocrAliyunAccessKeyId);
        config.setAccessKeySecret(cfg.ocrAliyunAccessKeySecret);
        config.endpoint = cfg.ocrAliyunEndpoint;

        return new com.aliyun.ocr_api20210707.Client(config);
    }

    public static void main(String[] args) throws Exception {

        com.aliyun.ocr_api20210707.Client client = createClient();

        InputStream imageStream = new FileInputStream("d:/承兑承诺书.jpg");

        RecognizeAdvancedRequest request = new RecognizeAdvancedRequest();
        request.setBody(imageStream);
        request.setNeedRotate(true);
//        request.setOutputProbability(true);

        RuntimeOptions runtime = new RuntimeOptions();

        RecognizeAdvancedResponse response =
                client.recognizeAdvancedWithOptions(request, runtime);

        System.out.println(
                new GsonBuilder()
                        .setPrettyPrinting()
                        .create()
                        .toJson(response)
        );
    }
}
