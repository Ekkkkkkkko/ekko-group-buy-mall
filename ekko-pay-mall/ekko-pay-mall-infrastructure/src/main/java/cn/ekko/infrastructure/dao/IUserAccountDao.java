package cn.ekko.infrastructure.dao;

import cn.ekko.infrastructure.dao.po.UserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IUserAccountDao {

    UserAccount queryByUsername(@Param("username") String username);

    UserAccount queryByUserId(@Param("userId") String userId);

    int insert(UserAccount account);

    int updatePassword(
            @Param("userId") String userId,
            @Param("passwordHash") String passwordHash,
            @Param("expectedTokenVersion") int expectedTokenVersion);

    int revokeTokens(
            @Param("userId") String userId,
            @Param("expectedTokenVersion") int expectedTokenVersion);
}
