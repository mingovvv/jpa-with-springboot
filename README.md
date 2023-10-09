# JPA with Springboot

-----

springboot 3.0 이상
 - java17 이상

query parameter 로그로 확인하기
 - https://github.com/gavlyukovskiy/spring-boot-data-source-decorator
 - boot3 : `implementation 'com.github.gavlyukovskiy:p6spy-spring-boot-starter:1.5.6'`
 - boot2 : `implementation 'com.github.gavlyukovskiy:p6spy-spring-boot-starter:1.9.0'`

@Transactional 
 - jpa 모든 데이터 변경과 로직은 트랙잭션 범위내에서 실행되어야 함
 - `readOnly = true` : 조회 시, 최적화(dirty checking x)

테스트케이스 In-Memory 환경
 - `src/test` 하위 resources 폴더 생성(test 구동 시, `src/main/resources` 보다 우선권을 가짐)
 - `spring.datasource.url : jdbc:h2:mem:test` : In-Memory H2 DB 설정

Entity
 - `@NoArgsConstructor(access = AccessLevel.PROTECTED` 설정하기
 - 기본적으로 엔티티는 리플랙션 기술을 사용하기 때문에 `기본 생성자 필요`
 - 접근레벨을 protected로 설정하여, 외부로부터의 생성을 막고 좀 더 객체지향 스럽게


변경 감지(dirty-checking))와 병합(merge)
 - merge
   - 준영속(영속성 컨텍스트가 관리하지 않는) 엔티티를 영속 상태로 변경하면서 업데이트를 해주는 메서드
   - 준영속 엔티티의 식별자(@id) 값으로 1차 캐시에서 엔티티를 조회
   - 1차 캐시에 없으면 DB를 통해 데이터를 조회하고 1차 캐시에 저장
   - 조회한 엔티티(DB 상태)를 파라미터로 넘긴 엔티티의 값으로 orverride
   - orverride된 값들은 dirty-checking에 의해서 업데이트
   - null값으로 업데이트 될 수 있으므로 실무에서 사용하지 말 것...
     ![Desktop View](/images/1.png)
 - dirty-checking
   - 원라는 필드만 변경 가능

Entity는 외부에 노출 X → DTO 사용할 것
 - 엔티티와 프레젠테이션 계층은 분리하는 것이 맞음
 - 엔티티에 API 파라미터 검증을 위한 로직이 들어가는 것 방지(ex. @NotEmpty()...)
 - 엔티티의 필드로 API를 위한 모든 데이터를 담을 수 없는 경우도 많음
 - 엔티티가 변경되면 API spec이 변함
 - `응답값으로 엔티티를 설정해두었다면?`
   1. 양방향 연관관계가 있다면 json으로 변환 시, 무한 순회참조로 stackoverflow 발생...
   2. 연관관계 설정이 지연로딩(Lazy)였다면, Proxy객체가 반환되어 이걸 json으로 변화하려다가 error 발생...

지연 로딩(LAZY)을 피하기 위해 즉시 로딩(EARGR)으로 설정하면 안된다.(지연이든 즉시든 `JPQL`의 경우, 똑같이 N + 1 문제가 발생함) 
즉시 로딩 때문에 연관관계가 필요없는 경우에도 데이터를 항상 조회해서 성능 문제가 발생할 수 있다. 
즉시 로딩으로 설정하면 성능튜닝이 매우 어려워 진다.

Entity 조회 시, DTO로 데이터 받기
```java
public List<OrderSimpleQueryDto> findOrderDtos() {
    return em.createQuery(
         "select new com.example.jpa.dto.OrderSimpleQueryDto(o.id, m.name, o.orderDate, o.status, d.address)" +
         " from Order o" +
                 " join o.member m" +
                 " join o.delivery d", OrderSimpleQueryDto.class)
    .getResultList();
 }
```
 - 기본적으로 entity 타입과 값 타입만 select의 결과로 가져올 수 있음
 - `new com.example.jpa.dto.OrderSimpleQueryDto` (`new` + `DTO 경로`) 를 통해 생성할 DTO 명시하기 
 - 생성자로 entity 자체를 넣으면 식별자만 넘어감... 하나씩 생성자에 파라미터를 넣어줘야 함.
 - 원하는 필드만 가져오는 장점이 있으나 반대로 재사용성이 낮다.

컬렉션 조회 최적화
![Desktop View](/images/2.png)
 - `1 : N 조회 시`, 1이 1개 N이 10개면 데이터가 뻥튀기 되어 총 10개가 조회되는 컬렉션 조회
   - ex) 1명의 맴버가 3개의 팀을 가지고 있다면, join 시 3개의 row를 반환하는 것처럼

```java
public List<Order> findAllWithItem() {
   return em.createQuery(
                  "select distinct o from Order o" +
                          " join fetch o.member m" +
                          " join fetch o.delivery d" +
                          " join fetch o.orderItems oi" +
                          " join fetch oi.item i", Order.class)
          .getResultList();
}
```
 - `distinct` 키워드 추가
   1. DB의 distinct와 동일하게 동작
   2. JPQL에서는 추가적으로 distinct 키워드를 통해 `from절의 엔티티` 중복을 제거
 - `페이징 처리는 불가능!!!`
   - 경고로그 : `firstResult/maxResults specified with collection fetch; applying in memory!`
   - 콜렉션 페치조인과 같이 페이징 처리가 정의되었으니 애플리케이션 메모리단에서 페이징 처리하겠다 → 전체 조회를 의미

컬렉션 페이징 처리 방법(정규화된 상태로 조회하는 방법)
  1. `N : 1(xxxToOne)`은 모두 `fetch join`을 그대로 사용(row수를 증가시키지 않으므로 상관없음) 
  2. limit / offset 을 설정
  3. 컬렉션 부분은 지연로딩으로 처리됨
  4. 지연로딩의 최적화를 위해 아래 두 옵션 중 하나를 선택 (컬렉션이나, 프록시 객체를 한꺼번에 설정한 size 만큼 `IN 쿼리`로 조회)
     - 글로벌 설정 : `hibernate.default_batch_fetch_size` 
     - 개별 설정 : `@BatchSize`


OSIV(Open Session In View)
![Desktop View](/images/3.png)
 - `spring.jpa.open-in-view` : true 기본값
 - true로 설정하면 영속성 컨텍스트와 커넥션 API 응답이 끝날때 까지 살아있음(db 커넥션이 말라버릴수도 있음)
 - false로 유지하는것이 좋아보임
 - OSIV를 끄면 트랜잭션을 종료할 때 영속성 컨텍스트를 닫고, 데이터베이스 커넥션도 반환한다. 따라서 커넥션 리소스를 낭비하지 않는다.
 - OSIV를 끄면 모든 지연로딩을 트랜잭션 안에서 처리해야 함