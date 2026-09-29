package com.example.toolhub;

import com.example.toolhub.domain.entity.Category;
import com.example.toolhub.domain.enums.Role;
import com.example.toolhub.dto.request.RegisterRequest;
import com.example.toolhub.repository.CategoryRepository;
import com.example.toolhub.repository.UserRepository;
import com.example.toolhub.service.UserRegistrationService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Local review launcher. Test sources and H2 are excluded from the production JAR. */
public class Sprint3PreviewApplication {
    public static void main(String[] args) {
        var app = new SpringApplication(ToolHubApplication.class, Fixtures.class);
        app.run("--spring.profiles.active=test", "--server.address=127.0.0.1", "--server.port=18080",
                "--spring.datasource.url=jdbc:h2:mem:sprint3_preview;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "--spring.datasource.driver-class-name=org.h2.Driver", "--spring.datasource.username=sa",
                "--spring.datasource.password=", "--server.servlet.session.cookie.secure=false");
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class Fixtures {
        @Bean
        ApplicationRunner reviewAccounts(UserRegistrationService registration, UserRepository users,
                                         CategoryRepository categories, com.example.toolhub.service.ToolService tools,
                                         com.example.toolhub.service.ToolVersionService versions, com.example.toolhub.service.PublishingService publishing) {
            return args -> {
                registration.register(new RegisterRequest("owner@sprint3.test", "ReviewOnly123!", "เจ้าของเครื่องมือ"));
                registration.register(new RegisterRequest("admin@sprint3.test", "ReviewOnly123!", "ผู้ตรวจอนุมัติ"));
                var admin = users.findByEmail("admin@sprint3.test").orElseThrow();
                admin.setRole(Role.ADMIN);
                users.saveAndFlush(admin);
                var category = categories.saveAndFlush(new Category("Developer tools", "developer-tools", "หมวดสำหรับตรวจเดโม"));
                var owner = users.findByEmail("owner@sprint3.test").orElseThrow();
                var ownerActor = new com.example.toolhub.security.CurrentActor(owner.getId(), false);
                var adminActor = new com.example.toolhub.security.CurrentActor(admin.getId(), true);
                for (String stage : new String[]{"draft", "pending", "published"}) {
                    var tool = tools.create(new com.example.toolhub.dto.request.CreateToolRequest(
                            "ตัวอย่าง " + stage, "preview-" + stage, "เครื่องมือสำหรับตรวจ Sprint 3",
                            "ข้อมูลชั่วคราวสำหรับลองฟอร์ม เวอร์ชัน และการอนุมัติ", category.getId(), null), owner.getId());
                    versions.create(tool.getId(), new com.example.toolhub.dto.request.ToolVersionRequest("1.0.0", "รุ่นตัวอย่างสำหรับตรวจงาน"), ownerActor);
                    if (!stage.equals("draft")) publishing.transition(tool.getId(), com.example.toolhub.domain.enums.PublishingAction.SUBMIT, ownerActor);
                    if (stage.equals("published")) publishing.transition(tool.getId(), com.example.toolhub.domain.enums.PublishingAction.APPROVE, adminActor);
                }
            };
        }
    }
}