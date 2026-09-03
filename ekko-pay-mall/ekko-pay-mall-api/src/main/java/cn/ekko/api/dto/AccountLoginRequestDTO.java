package cn.ekko.api.dto;

import lombok.Data;

@Data
public class AccountLoginRequestDTO {

    private String username;
    private String password;
}
