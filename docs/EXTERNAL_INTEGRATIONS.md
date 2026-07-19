# 외부 연동 제약과 검증 원칙

## 공통 원칙

- 외부 API Endpoint, 필드, 상태, Rate Limit, Sandbox 기능을 추측하지 않는다.
- 공식 문서의 버전과 확인 날짜를 기록한다.
- 외부 응답 원본과 내부 정규화 모델을 분리한다.
- 알 수 없는 Enum·오류 코드는 즉시 위험한 기본값으로 매핑하지 않고 격리한다.
- 외부 콘텐츠 저장·가공·LLM 전송·재배포 권리는 공급자 계약을 우선한다.
- 비밀정보를 문서·Fixture·로그·이벤트에 넣지 않는다.

## Toss Securities Open API 확인 기준선

2026-07-19 공식 공개 문서를 읽기 전용으로 재확인했다. 실제 인증·계좌·주문 호출은 수행하지 않았다.

- 공식 가이드: <https://developers.tossinvest.com/docs>
- LLM 안내: <https://developers.tossinvest.com/llms.txt>
- Canonical OAS: <https://openapi.tossinvest.com/openapi-docs/latest/openapi.json>
- 확인 당시 OAS 버전: `1.2.4`, OpenAPI `3.1.0`

확인된 범위는 다음과 같다.

- OAuth 2.0 Client Credentials 기반 인증
- 국내·미국 주식 시세·종목·시장 정보
- 계좌·보유 자산 조회
- 주문 생성·정정·취소·조회 및 주문 가능 정보
- 시장 캘린더와 세션 정보
- 현재 Open API 연동은 REST이며 WebSocket은 향후 지원 예정
- 주문 생성의 선택적 `clientOrderId` 멱등성 보장 기간은 10분
- 가격·수량·금액은 Decimal 문자열 중심
- Rate Limit 관련 응답 Header와 오류 모델 제공

## 구현 시 반드시 재검증할 항목

공식 문서는 변경될 수 있으므로 Broker Adapter 구현 직전에 OAS를 다시 확인하고 승인된 Snapshot을 Contract Test 입력으로 고정한다.

- 정확한 Endpoint와 요청·응답 Schema
- Rate Limit Group별 실제 한도와 우선순위
- 주문 상태·정정·취소 상태 전이
- 종료 주문 목록의 조회 기간·페이징·상태 분류
- 개별 체결 ID·체결 목록 제공 여부
- `clientOrderId`로 주문을 조회할 수 있는지 여부
- 모호한 응답 이후 안전한 복구 수단
- 시장별 주문 유형·Time In Force·세션 제한
- Paper/Sandbox 환경의 공식 제공 여부
- 조회 권한과 주문 권한 분리 가능 여부
- 등록 IP·Credential 회전·토큰 만료 정책

2026-07-19 OAS `1.2.4`에서는 `status=CLOSED` 주문 목록 조회와 모든 상태의 개별 주문 상세 조회를 확인했다. Reconciliation은 이 기능을 사용할 수 있도록 설계하되, Broker Adapter 구현 직전에 조회 기간·페이징·상태 Schema를 다시 검증하고 승인된 OAS Snapshot으로 Contract Test를 고정한다.

공식 멱등성 10분은 시스템 전체의 중복 방지 보장이 아니다. 내부 idempotency key와 주문 시도 기록은 더 오래 보존하며, 유효 기간 이후 같은 `clientOrderId`를 자동 재전송하지 않는다.

## 초기 Market Collector 제약

`RECOMMENDED` 초기 Collector는 REST 폴링을 사용한다.

- 공식 1분봉·일봉과 현재가·호가·최근 체결을 구분한다.
- 최근 체결 조회를 완전한 거래소 틱 스트림으로 표현하지 않는다.
- 데이터 완전성은 REST 예정 수집 슬롯 대비 성공률과 Gap으로 측정한다.
- 주문·계좌·시장 상태 조회에 필요한 Rate Limit 예산을 시세 폴링보다 우선한다.
- 공식 WebSocket이 제공되면 별도 Adapter와 ADR을 통해 교체한다.

## 뉴스 공급자

뉴스 공급자는 `TBD`다. 공급자를 선택하기 전까지 합성·라이선스 허용 Fixture만 사용한다.

선정 시 다음을 문서화한다.

- 국내·미국 종목과 기업 매핑 범위
- 발행 시각·수정·삭제·중복 식별 지원
- 원문·요약·제목·링크별 저장 가능 범위
- LLM 분석과 파생 특징 저장 권리
- 재배포·Dashboard 노출 권리
- 보존 기간과 계약 종료 시 삭제 의무
- Rate Limit, Backfill, 장애·지원 정책
- 출처 신뢰도 평가에 사용할 수 있는 메타데이터

뉴스는 항상 비신뢰 데이터다. 기사 속 명령문은 Agent 지시나 Tool Call로 승격하지 않는다.

## LLM 공급자

LLM 공급자와 모델은 `TBD`다. 선택 기준은 다음과 같다.

- 구조화 출력 성공률
- 한국어·금융 사건 분류 정확도
- 근거 데이터 일치율과 근거 없는 주장 비율
- 데이터 보관·학습 사용 정책
- 지역·보안·개인정보 조건
- 비용·지연·Rate Limit·장애 정책
- 모델·API 버전 고정과 변경 공지

공급자를 선택해도 주문 자격증명이나 자유 도구 권한은 제공하지 않는다.
