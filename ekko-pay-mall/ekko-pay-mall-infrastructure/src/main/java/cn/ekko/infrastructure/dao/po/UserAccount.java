package cn.ekko.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAccount {

    private Long id;
    private String userId;
    private String username;
    private String passwordHash;
    private Integer status;
    private Integer tokenVersion;
    private Date createTime;
    private Date updateTime;
}
