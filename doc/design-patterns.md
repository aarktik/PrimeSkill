# Design Patterns — PrimeSkill

อ้างอิง develop SHA `4678e0047184de7100579041c9cdfacfe228c688`; ตรวจวันที่ 10 ตุลาคม 2026

## Enterprise / Architectural Patterns

| Pattern | ปัญหาที่แก้ | ไฟล์/คลาสที่ใช้ | Class Diagram ประกอบ |
| --- | --- | --- | --- |
| Layered Architecture | แยก HTTP, use cases, persistence และ domain เพื่อลดการผูก UI กับฐานข้อมูล | [controller/api/ToolRestController.java:26](https://github.com/aarktik/PrimeSkill/blob/4678e0047184de7100579041c9cdfacfe228c688/code/src/main/java/com/example/toolhub/controller/api/ToolRestController.java#L26); [service/impl/ToolServiceImpl.java:24](https://github.com/aarktik/PrimeSkill/blob/4678e0047184de7100579041c9cdfacfe228c688/code/src/main/java/com/example/toolhub/service/impl/ToolServiceImpl.java#L24); [repository/ToolRepository.java:17](https://github.com/aarktik/PrimeSkill/blob/4678e0047184de7100579041c9cdfacfe228c688/code/src/main/java/com/example/toolhub/repository/ToolRepository.java#L17); domain/entity | [Layers](diagrams/design-patterns.md#layered-mvc-repository-service-dto-and-di) |
| MVC | แยก request handling, model/DTO และ HTML rendering | controller/web/ToolWebController.java; domain/entity/Tool.java; dto/response/ToolResponse.java; code/src/main/resources/templates/tools | [MVC](diagrams/design-patterns.md#layered-mvc-repository-service-dto-and-di) |
| Repository | รวม JPA queries/locks ที่ขอบเขต persistence | [repository/ToolRepository.java:17](https://github.com/aarktik/PrimeSkill/blob/4678e0047184de7100579041c9cdfacfe228c688/code/src/main/java/com/example/toolhub/repository/ToolRepository.java#L17); repository/ReviewRepository.java; repository/ToolVersionRepository.java (Spring Data JPA) | [Repository](diagrams/design-patterns.md#layered-mvc-repository-service-dto-and-di) |
| Service Layer | รวม transaction และ business rules ที่ใช้ได้ทั้ง Web/REST | [service/impl/ToolServiceImpl.java:24](https://github.com/aarktik/PrimeSkill/blob/4678e0047184de7100579041c9cdfacfe228c688/code/src/main/java/com/example/toolhub/service/impl/ToolServiceImpl.java#L24); service/impl/ReviewServiceImpl.java; service/impl/PublishingServiceImpl.java | [Service](diagrams/design-patterns.md#layered-mvc-repository-service-dto-and-di) |
| DTO + Mapper | แยก API contract จาก persistence Entity และควบคุมข้อมูล response | [mapper/ToolMapper.java:8](https://github.com/aarktik/PrimeSkill/blob/4678e0047184de7100579041c9cdfacfe228c688/code/src/main/java/com/example/toolhub/mapper/ToolMapper.java#L8); dto/request/CreateToolRequest.java; dto/response/ToolResponse.java | [DTO/Mapper](diagrams/design-patterns.md#layered-mvc-repository-service-dto-and-di) |
| Dependency Injection | ให้ Spring ประกอบ dependencies และเปลี่ยน collaborators ในการทดสอบ | [service/impl/ReviewServiceImpl.java:41](https://github.com/aarktik/PrimeSkill/blob/4678e0047184de7100579041c9cdfacfe228c688/code/src/main/java/com/example/toolhub/service/impl/ReviewServiceImpl.java#L41); ToolRestController constructor; ToolServiceImpl constructor | [DI](diagrams/design-patterns.md#layered-mvc-repository-service-dto-and-di) |

Mapper ยังเป็น concrete component และ ToolVersionServiceImpl ยัง map response ภายใน จึงอธิบาย Pattern ที่มีอยู่โดยไม่รับรองว่าทุก mapping ถูกแยกแล้ว ดูข้อจำกัดใน [SOLID analysis](solid-analysis.md)

## GoF — กลุ่ม Behavioral (3 แบบ)

| Pattern | ปัญหาที่แก้ | ไฟล์/คลาสที่ใช้ | Class Diagram ประกอบ |
| --- | --- | --- | --- |
| Strategy | เปลี่ยนลำดับ newest/popular โดยเลือก ordering policy ผ่าน interface | [service/search/ToolSortStrategy.java:5](https://github.com/aarktik/PrimeSkill/blob/4678e0047184de7100579041c9cdfacfe228c688/code/src/main/java/com/example/toolhub/service/search/ToolSortStrategy.java#L5); NewestToolSortStrategy; PopularityToolSortStrategy; [service/impl/ToolSearchServiceImpl.java:82](https://github.com/aarktik/PrimeSkill/blob/4678e0047184de7100579041c9cdfacfe228c688/code/src/main/java/com/example/toolhub/service/impl/ToolSearchServiceImpl.java#L82) | [Strategy](diagrams/design-patterns.md#strategy) |
| State | รวมกฎการเปลี่ยน DRAFT/PENDING/PUBLISHED/DEPRECATED ใน state handlers แทนกระจายใน controllers | [service/publishing/PublishingState.java:6](https://github.com/aarktik/PrimeSkill/blob/4678e0047184de7100579041c9cdfacfe228c688/code/src/main/java/com/example/toolhub/service/publishing/PublishingState.java#L6); [service/publishing/PublishingStateMachine.java:12](https://github.com/aarktik/PrimeSkill/blob/4678e0047184de7100579041c9cdfacfe228c688/code/src/main/java/com/example/toolhub/service/publishing/PublishingStateMachine.java#L12); DraftState/PendingState/PublishedState/DeprecatedState (nested classes) | [State](diagrams/design-patterns.md#state) |
| Observer | บันทึก audit หลังสร้างรีวิวโดยไม่ผูก ReviewService กับ audit listener และไม่ log ก่อน transaction commit | [service/impl/ReviewServiceImpl.java:90](https://github.com/aarktik/PrimeSkill/blob/4678e0047184de7100579041c9cdfacfe228c688/code/src/main/java/com/example/toolhub/service/impl/ReviewServiceImpl.java#L90); event/ReviewCreatedEvent.java; [event/ReviewAuditListener.java:14](https://github.com/aarktik/PrimeSkill/blob/4678e0047184de7100579041c9cdfacfe228c688/code/src/main/java/com/example/toolhub/event/ReviewAuditListener.java#L14) | [Observer](diagrams/design-patterns.md#observer) |

### เหตุผลการเลือกและการใช้งานจริง

- **Strategy:** ToolSearchServiceImpl รับ List<ToolSortStrategy> ทาง constructor และ resolveSort() เลือก implementation ก่อนสร้าง pageable; newest/popular ถูกใช้จริง Rating/Relevance มี strategy components แต่ query ordering ยังแยกอยู่ใน Service/repository จึงยังมี OCP limitation ไม่ถือว่าแก้ด้วย Strategy ครบทุก sort
- **State:** PublishingServiceImpl เรียก stateMachine.transition(); state handlers เลือก next status ตาม PublishingAction ส่วนการตรวจผู้กระทำ/revision/locks อยู่ที่ Service Invalid action เป็น domain failure ตามสัญญา ไม่ใช่ UnsupportedOperationException
- **Observer:** ReviewServiceImpl publish ReviewCreatedEvent หลัง save; ReviewAuditListener ใช้ @TransactionalEventListener(AFTER_COMMIT) บันทึก audit เมื่อ commit สำเร็จ Listener รับ event ผ่าน Spring ไม่ได้ถูกเรียกตรงโดย Service

เลือก Behavioral ทั้งสามเพราะมีปัญหาการสลับ policy, lifecycle และ side effect หลัง commit อยู่จริง ไม่อ้าง Spring singleton หรือ DTO builder เพิ่มเป็น GoF ที่เลือกเพื่อให้ครบจำนวน

## สถานะหลักฐาน

เอกสารและ diagrams อธิบายโค้ดปัจจุบัน ไม่เพิ่ม runtime Pattern ใหม่ รอบนี้ไม่มีการแก้ API/UI/schema/permissions และไม่ได้อ้างว่าผ่าน SOLID ทุกข้อ ดูรายการที่ยังต้องปรับใน [solid-analysis.md](solid-analysis.md)
