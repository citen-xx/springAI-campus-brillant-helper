package com.ruoyi.system.Oss;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.OSSObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 阿里云 OSS 服务
 */
@Service
public class AliOssService
{
    @Value("${aliyun.oss.endpoint}")
    private String endpoint;

    @Value("${aliyun.oss.access-key-id}")
    private String accessKeyId;

    @Value("${aliyun.oss.access-key-secret}")
    private String accessKeySecret;

    @Value("${aliyun.oss.bucket-name}")
    private String bucketName;

    @Value("${aliyun.oss.directory:campus-ai}")
    private String directory;

    /**
     * 上传文件到阿里云 OSS
     *
     * @param multipartFile 上传文件
     * @return 文件访问 URL
     */
    public String upload(MultipartFile multipartFile)
    {
        if (multipartFile == null || multipartFile.isEmpty())
        {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        String originalFilename = multipartFile.getOriginalFilename();
        String suffix = getSuffix(originalFilename);
        String objectKey = buildObjectKey(suffix);

        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try (InputStream inputStream = multipartFile.getInputStream())
        {
            ossClient.putObject(bucketName, objectKey, inputStream);
            return buildFileUrl(objectKey);
        }
        catch (IOException e)
        {
            throw new RuntimeException("上传文件到 OSS 失败", e);
        }
        finally
        {
            ossClient.shutdown();
        }
    }

    /**
     * 读取 OSS 对象输入流
     *
     * @param objectKey OSS 对象路径
     * @return 输入流
     */
    public InputStream getObjectInputStream(String objectKey)
    {
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try
        {
            OSSObject ossObject = ossClient.getObject(bucketName, objectKey);
            return new OssObjectInputStream(ossClient, ossObject);
        }
        catch (Exception e)
        {
            ossClient.shutdown();
            throw new RuntimeException("读取 OSS 文件失败: " + objectKey, e);
        }
    }

    /**
     * 通过 OSS 文件 URL 读取输入流
     *
     * @param fileUrl OSS 文件 URL
     * @return 输入流
     */
    public InputStream getObjectInputStreamByUrl(String fileUrl)
    {
        return getObjectInputStream(extractObjectKey(fileUrl));
    }

    /**
     * 从 OSS 访问 URL 提取对象路径
     *
     * @param fileUrl 文件 URL
     * @return OSS objectKey
     */
    public String extractObjectKey(String fileUrl)
    {
        if (fileUrl == null || fileUrl.isBlank())
        {
            throw new IllegalArgumentException("fileUrl 不能为空");
        }

        String normalizedEndpoint = endpoint.startsWith("http://") || endpoint.startsWith("https://")
            ? endpoint
            : "https://" + endpoint;
        String domainPrefix = normalizedEndpoint.replace("://", "://" + bucketName + ".") + "/";
        if (!fileUrl.startsWith(domainPrefix))
        {
            throw new IllegalArgumentException("非法的 OSS 文件 URL: " + fileUrl);
        }
        return fileUrl.substring(domainPrefix.length());
    }

    private String buildObjectKey(String suffix)
    {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return directory + "/" + datePath + "/" + UUID.randomUUID().toString().replace("-", "") + suffix;
    }

    private String getSuffix(String originalFilename)
    {
        if (originalFilename == null || !originalFilename.contains("."))
        {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf('.'));
    }

    private String buildFileUrl(String objectKey)
    {
        String normalizedEndpoint = endpoint.startsWith("http://") || endpoint.startsWith("https://")
            ? endpoint
            : "https://" + endpoint;
        return normalizedEndpoint.replace("://", "://" + bucketName + ".") + "/" + objectKey;
    }

    /**
     * 包装 OSS 输入流，确保关闭流时同时关闭 OSSClient。
     */
    private static class OssObjectInputStream extends InputStream
    {
        private final OSS ossClient;
        private final OSSObject ossObject;
        private final InputStream delegate;

        private OssObjectInputStream(OSS ossClient, OSSObject ossObject)
        {
            this.ossClient = ossClient;
            this.ossObject = ossObject;
            this.delegate = ossObject.getObjectContent();
        }

        @Override
        public int read() throws IOException
        {
            return delegate.read();
        }

        @Override
        public int read(byte[] b) throws IOException
        {
            return delegate.read(b);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException
        {
            return delegate.read(b, off, len);
        }

        @Override
        public void close() throws IOException
        {
            try
            {
                delegate.close();
            }
            finally
            {
                try
                {
                    ossObject.close();
                }
                finally
                {
                    ossClient.shutdown();
                }
            }
        }
    }
}
