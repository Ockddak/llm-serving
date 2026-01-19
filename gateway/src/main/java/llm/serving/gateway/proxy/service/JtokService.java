package llm.serving.gateway.proxy.service;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.stereotype.Service;

/**
 * LLM에 보내기 전 해당 요청이 대략 몇 토큰으로 구성 되어 있는지 확인하는 역할
 * JTokkit cl100k_base 인코딩 제공
 * LLaMA 3 계열과 토큰 분해 방식이 상당히 유사
 */
@Service
public class JtokService {
    private final Encoding encoding;

    public JtokService() {
        // 토큰화 하는 기준 설정
        this.encoding = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
    }

    public long estimateTokensFromBody(String body) {
        // body가 JSON이면 필요한 fields(예: prompt)만 뽑아서 인코딩하세요
        // BPE(Byte Pair Encoding) 방식을 사용하여 토큰 개수 계산
        return this.encoding.encode(body).size();
    }
}
