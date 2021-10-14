package com.mz.jarboot.dao;

import com.mz.jarboot.entity.RoleInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author jianzhengma
 */
@Repository
public interface RoleDao extends JpaRepository<RoleInfo, Long> {
    /**
     * 删除角色
     * @param role 角色
     */
    @Modifying
    void deleteAllByRole(String role);

    /**
     * 根据角色和用户名删除
     * @param role 角色
     * @param username 用户名
     */
    @Modifying
    void deleteByRoleAndUsername(String role, String username);

    /**
     * 根据用户名获取角色
     * @param username 用户名
     * @param pageable 分页
     * @return 角色信息列表
     */
    @Query(value = "select r from RoleInfo r where r.username=:username")
    Page<RoleInfo> getRoleByUsername(@Param("username") String username, Pageable pageable);

    /**
     * 根据角色关键字寻找角色
     * @param role 关键字
     * @return 角色列表
     */
    @Query("select r.role from RoleInfo r where r.role like CONCAT('%',:role,'%')")
    List<String> findRolesLikeRoleName(@Param("role") String role);

    /**
     * 获取第一个符合指定角色和用户的角色信息
     * @param role 角色
     * @param username 用户名
     * @return 角色信息
     */
    RoleInfo findFirstByRoleAndUsername(String role, String username);

    /**
     * 获取所有角色列表
     * @return 角色列表
     */
    @Query("select distinct r.role from RoleInfo r")
    List<String> getRoleList();

    /**
     * 根据角色获取第一个角色信息
     * @param role 角色
     * @return 角色信息
     */
    RoleInfo findFirstByRole(String role);

    /**
     * 统计角色数量
     * @return 数量
     */
    @Query("select count(distinct r.role) from RoleInfo r")
    long countRoles();
}
