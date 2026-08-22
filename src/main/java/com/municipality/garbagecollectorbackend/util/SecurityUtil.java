package com.municipality.garbagecollectorbackend.util;

import com.municipality.garbagecollectorbackend.model.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtil {

    public static User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails cud) {
            return cud.getUser();
        }
        return null;
    }

    public static boolean isSuperAdmin() {
        User user = getCurrentUser();
        return user != null && user.getRole() == User.Role.SUPER_ADMIN;
    }

    public static String getCurrentDepartmentId() {
        User user = getCurrentUser();
        return user != null ? user.getDepartmentId() : null;
    }

    public static boolean canAccessDepartment(String departmentId) {
        if (isSuperAdmin()) {
            return true;
        }
        String userDeptId = getCurrentDepartmentId();
        return userDeptId != null && userDeptId.equals(departmentId);
    }
}
