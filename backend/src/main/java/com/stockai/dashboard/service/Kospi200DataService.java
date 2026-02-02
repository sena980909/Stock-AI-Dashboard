package com.stockai.dashboard.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 코스피 200 종목 데이터 서비스
 * 종목코드, 종목명 매핑 및 검색 기능 제공
 */
@Service
@Slf4j
public class Kospi200DataService {

    @Getter
    private final Map<String, String> stockCodeToName = new LinkedHashMap<>();

    @Getter
    private final Map<String, String> stockNameToCode = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        initializeKospi200Stocks();
        log.info("[Kospi200DataService] Initialized with {} stocks", stockCodeToName.size());
    }

    /**
     * 코스피 200 종목 초기화 (2024년 기준 주요 종목)
     */
    private void initializeKospi200Stocks() {
        // 시가총액 상위 종목들 (약 200개)
        addStock("005930", "삼성전자");
        addStock("000660", "SK하이닉스");
        addStock("373220", "LG에너지솔루션");
        addStock("207940", "삼성바이오로직스");
        addStock("005380", "현대차");
        addStock("000270", "기아");
        addStock("068270", "셀트리온");
        addStock("035420", "NAVER");
        addStock("005490", "POSCO홀딩스");
        addStock("035720", "카카오");
        addStock("006400", "삼성SDI");
        addStock("051910", "LG화학");
        addStock("028260", "삼성물산");
        addStock("003670", "포스코퓨처엠");
        addStock("012330", "현대모비스");
        addStock("055550", "신한지주");
        addStock("066570", "LG전자");
        addStock("105560", "KB금융");
        addStock("096770", "SK이노베이션");
        addStock("034730", "SK");
        addStock("003550", "LG");
        addStock("032830", "삼성생명");
        addStock("086790", "하나금융지주");
        addStock("017670", "SK텔레콤");
        addStock("018260", "삼성에스디에스");
        addStock("030200", "KT");
        addStock("009150", "삼성전기");
        addStock("033780", "KT&G");
        addStock("000810", "삼성화재");
        addStock("010130", "고려아연");
        addStock("011200", "HMM");
        addStock("016360", "삼성증권");
        addStock("024110", "기업은행");
        addStock("010950", "S-Oil");
        addStock("011170", "롯데케미칼");
        addStock("036570", "엔씨소프트");
        addStock("015760", "한국전력");
        addStock("009540", "HD한국조선해양");
        addStock("034020", "두산에너빌리티");
        addStock("329180", "HD현대중공업");
        addStock("010140", "삼성중공업");
        addStock("011780", "금호석유");
        addStock("000720", "현대건설");
        addStock("090430", "아모레퍼시픽");
        addStock("035250", "강원랜드");
        addStock("004020", "현대제철");
        addStock("021240", "코웨이");
        addStock("047050", "포스코인터내셔널");
        addStock("001570", "금양");
        addStock("018880", "한온시스템");
        addStock("161390", "한국타이어앤테크놀로지");
        addStock("000100", "유한양행");
        addStock("051900", "LG생활건강");
        addStock("009830", "한화솔루션");
        addStock("138040", "메리츠금융지주");
        addStock("267260", "HD현대일렉트릭");
        addStock("377300", "카카오페이");
        addStock("011070", "LG이노텍");
        addStock("012450", "한화에어로스페이스");
        addStock("302440", "SK바이오사이언스");
        addStock("003490", "대한항공");
        addStock("005940", "NH투자증권");
        addStock("036460", "한국가스공사");
        addStock("000080", "하이트진로");
        addStock("004990", "롯데지주");
        addStock("326030", "SK바이오팜");
        addStock("006800", "미래에셋증권");
        addStock("128940", "한미약품");
        addStock("097950", "CJ제일제당");
        addStock("005830", "DB손해보험");
        addStock("005850", "에스엘");
        addStock("088980", "맥쿼리인프라");
        addStock("005387", "현대차2우B");
        addStock("003410", "쌍용C&E");
        addStock("402340", "SK스퀘어");
        addStock("352820", "하이브");
        addStock("361610", "SK아이이테크놀로지");
        addStock("316140", "우리금융지주");
        addStock("241560", "두산밥캣");
        addStock("000990", "DB하이텍");
        addStock("039490", "키움증권");
        addStock("052690", "한전기술");
        addStock("006360", "GS건설");
        addStock("069500", "KODEX 200");
        addStock("034220", "LG디스플레이");
        addStock("078930", "GS");
        addStock("008770", "호텔신라");
        addStock("042700", "한미반도체");
        addStock("000150", "두산");
        addStock("000120", "CJ대한통운");
        addStock("005300", "롯데칠성");
        addStock("001040", "CJ");
        addStock("016800", "퍼시스");
        addStock("139480", "이마트");
        addStock("282330", "BGF리테일");
        addStock("005880", "대한해운");
        addStock("004370", "농심");
        addStock("271560", "오리온");
        addStock("011790", "SKC");
        addStock("010620", "현대미포조선");
        addStock("032640", "LG유플러스");
        addStock("009420", "한올바이오파마");
        addStock("000070", "삼양홀딩스");
        addStock("007070", "GS리테일");
        addStock("086280", "현대글로비스");
        addStock("272210", "한화시스템");
        addStock("039130", "하나투어");
        addStock("005389", "현대차3우B");
        addStock("001450", "현대해상");
        addStock("008560", "메리츠증권");
        addStock("006260", "LS");
        addStock("001120", "LX인터내셔널");
        addStock("003620", "쌍용양회");
        addStock("020150", "일진머티리얼즈");
        addStock("010060", "OCI홀딩스");
        addStock("383220", "F&F");
        addStock("071050", "한국금융지주");
        addStock("064350", "현대로템");
        addStock("029780", "삼성카드");
        addStock("014680", "한솔케미칼");
        addStock("192820", "코스맥스");
        addStock("004170", "신세계");
        addStock("002790", "아모레G");
        addStock("023530", "롯데쇼핑");
        addStock("009240", "한샘");
        addStock("047810", "한국항공우주");
        addStock("006280", "녹십자");
        addStock("008930", "한미사이언스");
        addStock("012630", "현대산업");
        addStock("180640", "한진칼");
        addStock("000210", "대림산업");
        addStock("088350", "한화생명");
        addStock("001800", "오리온홀딩스");
        addStock("009970", "영원무역홀딩스");
        addStock("000240", "한국타이어");
        addStock("028050", "삼성엔지니어링");
        addStock("003240", "태광산업");
        addStock("007310", "오뚜기");
        addStock("011210", "현대위아");
        addStock("020560", "아시아나항공");
        addStock("002380", "KCC");
        addStock("009450", "경동나비엔");
        addStock("000880", "한화");
        addStock("000670", "영풍");
        addStock("001740", "SK네트웍스");
        addStock("047040", "대우건설");
        addStock("002960", "한국쉘석유");
        addStock("012750", "에스원");
        addStock("030000", "제일기획");
        addStock("008060", "대덕전자");
        addStock("036530", "S&T홀딩스");
        addStock("071320", "지역난방공사");
        addStock("002550", "LIG넥스원");
        addStock("057050", "현대홈쇼핑");
        addStock("069620", "대웅제약");
        addStock("004800", "효성");
        addStock("005250", "녹십자홀딩스");
        addStock("006120", "SK디스커버리");
        addStock("000480", "조선내화");
        addStock("029460", "케이씨");
        addStock("023150", "MDS테크");
        addStock("112610", "씨에스윈드");
        addStock("005935", "삼성전자우");
        addStock("051600", "한전KPS");
        addStock("001430", "세아베스틸지주");
        addStock("000060", "메리츠화재");
        addStock("003000", "부광약품");
        addStock("026960", "동서");
        addStock("192400", "쿠쿠홀딩스");
        addStock("001510", "SK증권");
        addStock("007700", "F&F홀딩스");
        addStock("036190", "금화PSC");
        addStock("001680", "대상");
        addStock("009410", "태영건설");
        addStock("000640", "동아쏘시오홀딩스");
        addStock("014820", "동원시스템즈");
        addStock("011760", "현대코퍼레이션");
        addStock("017800", "현대엘리베이터");
        addStock("073240", "금호타이어");
        addStock("005610", "SPC삼립");
        addStock("044820", "코스맥스비티아이");
        addStock("001230", "동국제강");
        addStock("120110", "코오롱인더");
        addStock("003850", "보령");
        addStock("000950", "전방");
        addStock("034730", "SK");
        addStock("051900", "LG생활건강");
        addStock("068760", "셀트리온제약");
        addStock("091990", "셀트리온헬스케어");
        addStock("263750", "펄어비스");
        addStock("293490", "카카오게임즈");
        addStock("035760", "CJ ENM");
        addStock("259960", "크래프톤");
        addStock("112040", "위메이드");
        addStock("041510", "에스엠");
        addStock("122870", "와이지엔터테인먼트");
        addStock("251270", "넷마블");
        addStock("095660", "네오위즈");
        addStock("194480", "데브시스터즈");
    }

    private void addStock(String code, String name) {
        stockCodeToName.put(code, name);
        stockNameToCode.put(name, code);
    }

    /**
     * 키워드로 종목 검색 (코드 또는 이름 포함)
     */
    public List<Map<String, String>> searchByKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String normalizedKeyword = keyword.trim().toLowerCase();
        List<Map<String, String>> results = new ArrayList<>();

        for (Map.Entry<String, String> entry : stockCodeToName.entrySet()) {
            String code = entry.getKey();
            String name = entry.getValue();

            // 코드가 키워드로 시작하거나, 이름에 키워드가 포함되면 결과에 추가
            if (code.startsWith(normalizedKeyword) ||
                name.toLowerCase().contains(normalizedKeyword)) {
                Map<String, String> stock = new LinkedHashMap<>();
                stock.put("code", code);
                stock.put("name", name);
                stock.put("market", "KOSPI");
                results.add(stock);
            }
        }

        // 정확히 매칭되는 것 우선 정렬
        results.sort((a, b) -> {
            String aName = a.get("name").toLowerCase();
            String aCode = a.get("code");
            String bName = b.get("name").toLowerCase();
            String bCode = b.get("code");

            // 정확히 일치하는 경우 우선
            boolean aExact = aName.equals(normalizedKeyword) || aCode.equals(normalizedKeyword);
            boolean bExact = bName.equals(normalizedKeyword) || bCode.equals(normalizedKeyword);
            if (aExact && !bExact) return -1;
            if (!aExact && bExact) return 1;

            // 이름이 키워드로 시작하는 경우 우선
            boolean aStarts = aName.startsWith(normalizedKeyword);
            boolean bStarts = bName.startsWith(normalizedKeyword);
            if (aStarts && !bStarts) return -1;
            if (!aStarts && bStarts) return 1;

            return 0;
        });

        return results;
    }

    /**
     * 코드로 종목명 조회
     */
    public String getNameByCode(String code) {
        return stockCodeToName.get(code);
    }

    /**
     * 종목명으로 코드 조회
     */
    public String getCodeByName(String name) {
        return stockNameToCode.get(name);
    }

    /**
     * 해당 코드가 코스피 200에 포함되는지 확인
     */
    public boolean isKospi200Stock(String code) {
        return stockCodeToName.containsKey(code);
    }

    /**
     * 전체 종목 수
     */
    public int getTotalCount() {
        return stockCodeToName.size();
    }
}
