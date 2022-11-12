package com.mz.jarboot.service.impl;

import com.mz.jarboot.common.JarbootException;
import com.mz.jarboot.common.ResponseForList;
import com.mz.jarboot.common.utils.StringUtils;
import com.mz.jarboot.constant.AuthConst;
import com.mz.jarboot.dao.PrivilegeDao;
import com.mz.jarboot.dao.RoleDao;
import com.mz.jarboot.dao.UserDao;
import com.mz.jarboot.entity.RoleInfo;
import com.mz.jarboot.entity.User;
import com.mz.jarboot.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * @author majianzheng
 */
@Service
public class RoleServiceImpl implements RoleService {
    @Autowired
    private RoleDao roleDao;
    @Autowired
    private UserDao userDao;
    @Autowired
    private PrivilegeDao privilegeDao;

    @Override
    public ResponseForList<RoleInfo> getRoles(int pageNo, int pageSize) {
        PageRequest page = PageRequest.of(pageNo, pageSize);
        Page<RoleInfo> all = roleDao.findAll(page);
        return new ResponseForList<>(all.getContent(), all.getTotalElements());
    }

    @Override
    public ResponseForList<RoleInfo> getRolesByUserName(String username, int pageNo, int pageSize) {
        Page<RoleInfo> page = roleDao.getRoleByUsername(username, PageRequest.of(pageNo, pageSize));
        return new ResponseForList<>(page.getContent(), page.getTotalElements());
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void addRole(String role, String username) {
        if (StringUtils.isEmpty(role) || StringUtils.isEmpty(username)) {
            throw new JarbootException("Argument can't be empty！");
        }
        if (AuthConst.ADMIN_ROLE.equalsIgnoreCase(role)) {
            throw new JarbootException("Role Admin is not permit to create！");
        }
        User user = userDao.findFirstByUsername(username);
        if (null == user) {
            throw new JarbootException("User name is not exist！");
        }
        if (null == roleDao.findFirstByRole(role) && roleDao.countRoles() > AuthConst.MAX_ROLE) {
            throw new JarbootException("Role number exceed " + AuthConst.MAX_ROLE + "!");
        }
        RoleInfo r = new RoleInfo();
        r.setRole(role);
        r.setUsername(username);
        roleDao.save(r);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void deleteRole(String role) {
        if (StringUtils.isEmpty(role)) {
            throw new JarbootException("Argument role can't be empty！");
        }
        if (AuthConst.ADMIN_ROLE.equals(role)) {
            throw new JarbootException("The internal role, can't delete.");
        }
        roleDao.deleteAllByRole(role);
        // 当前role已经没有任何关联的user，删除相关的权限
        privilegeDao.deleteAllByRole(role);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void deleteRole(String role, String username) {
        if (StringUtils.isEmpty(role) || StringUtils.isEmpty(username)) {
            throw new JarbootException("Argument role or name can't be empty！");
        }
        if (AuthConst.ADMIN_ROLE.equals(role) && AuthConst.JARBOOT_USER.equals(username)) {
            throw new JarbootException("The internal role, can't delete.");
        }
        roleDao.deleteByRoleAndUsername(role, username);
        if (null == roleDao.findFirstByRole(role)) {
            // 当前role已经没有任何关联的user，删除相关的权限
            privilegeDao.deleteAllByRole(role);
        }
    }

    @Override
    public List<String> findRolesLikeRoleName(String role) {
        return roleDao.findRolesLikeRoleName(role);
    }

    @Override
    public List<String> getRoleList() {
        return roleDao.getRoleList();
    }

    @Transactional(rollbackFor = Throwable.class)
    @PostConstruct
    public void init() {
        //检查是否存在ADMIN_ROLE，否则创建
        RoleInfo roleInfo = roleDao.findFirstByRoleAndUsername(AuthConst.ADMIN_ROLE, AuthConst.JARBOOT_USER);
        if (null != roleInfo) {
            return;
        }
        roleInfo = new RoleInfo();
        roleInfo.setUsername(AuthConst.JARBOOT_USER);
        roleInfo.setRole(AuthConst.ADMIN_ROLE);
        roleDao.save(roleInfo);
    }
}
