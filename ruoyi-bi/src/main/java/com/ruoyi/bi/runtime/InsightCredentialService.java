package com.ruoyi.bi.runtime;

import com.ruoyi.bi.api.BiException;
import com.ruoyi.bi.config.BiProperties;
import com.ruoyi.bi.datasource.CredentialCipher;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class InsightCredentialService {
    private final InsightCredentialRepository repository;private final CredentialCipher cipher;private final BiProperties properties;
    public InsightCredentialService(InsightCredentialRepository repository,CredentialCipher cipher,BiProperties properties){this.repository=repository;this.cipher=cipher;this.properties=properties;}

    public List<ProviderStatus> statuses(){return List.of(status("QWEN","阿里云千问",properties.insight().qwen().apiKey()),status("DEEPSEEK","DeepSeek",properties.insight().deepseek().apiKey()));}
    public ProviderStatus save(String rawProvider,SaveRequest request,long userId){String provider=provider(rawProvider);String key=request.apiKey().trim();try{repository.save(provider,cipher.encrypt(key),suffix(key),userId);}catch(IllegalStateException ex){throw new BiException(HttpStatus.SERVICE_UNAVAILABLE,"BI_INSIGHT_CREDENTIAL_KEY_UNAVAILABLE","服务端未配置凭据加密主密钥");}return status(provider,label(provider),fallback(provider));}
    public void delete(String rawProvider){repository.delete(provider(rawProvider));}
    String resolve(String rawProvider,String environmentFallback){return repository.find(provider(rawProvider)).map(item->{try{return cipher.decrypt(item.ciphertext());}catch(IllegalStateException ex){throw new BiException(HttpStatus.SERVICE_UNAVAILABLE,"BI_INSIGHT_CREDENTIAL_DECRYPT_FAILED","模型密钥无法解密，请重新配置");}}).orElse(environmentFallback);}

    private ProviderStatus status(String provider,String label,String fallback){return repository.find(provider).map(item->new ProviderStatus(provider,label,true,"DATABASE","••••"+item.suffix(),item.version(),item.updatedAt())).orElseGet(()->{boolean configured=fallback!=null&&!fallback.isBlank();return new ProviderStatus(provider,label,configured,configured?"ENVIRONMENT":"NONE",configured?"••••"+suffix(fallback):"",0,null);});}
    private String fallback(String provider){return "QWEN".equals(provider)?properties.insight().qwen().apiKey():properties.insight().deepseek().apiKey();}
    private static String provider(String value){String normalized=value==null?"":value.toUpperCase(Locale.ROOT);if(!List.of("QWEN","DEEPSEEK").contains(normalized))throw new BiException(HttpStatus.BAD_REQUEST,"BI_INSIGHT_PROVIDER_INVALID","模型供应商不合法");return normalized;}
    private static String label(String provider){return "QWEN".equals(provider)?"阿里云千问":"DeepSeek";}
    private static String suffix(String key){return key.substring(Math.max(0,key.length()-4));}
    public record SaveRequest(@NotBlank @Size(max=500) String apiKey){}
    public record ProviderStatus(String provider,String label,boolean configured,String source,String maskedKey,int credentialVersion,LocalDateTime updatedAt){}
}
