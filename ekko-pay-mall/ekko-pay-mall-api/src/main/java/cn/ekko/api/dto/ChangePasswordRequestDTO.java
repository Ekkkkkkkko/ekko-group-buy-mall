package cn.ekko.api.dto;

import lombok.Data;

@Data
public class ChangePasswordRequestDTO {

    private String currentPassword;
    private String newPassword;
}
