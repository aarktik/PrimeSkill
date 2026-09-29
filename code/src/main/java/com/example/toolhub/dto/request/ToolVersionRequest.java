package com.example.toolhub.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ToolVersionRequest(
        @NotBlank(message = "กรุณาระบุเลขเวอร์ชัน") @Size(max = 100, message = "เลขเวอร์ชันต้องไม่เกิน 100 ตัวอักษร") String version,
        @Size(max = 2000, message = "บันทึกการเปลี่ยนแปลงต้องไม่เกิน 2000 ตัวอักษร") String releaseNotes) {
}
