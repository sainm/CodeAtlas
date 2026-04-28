package com.acme.legacy.web;

import com.acme.legacy.service.UserService;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;

public class UserAction extends Action {
    private final UserService userService = new UserService();

    public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
        UserForm userForm = (UserForm) form;
        String userId = request.getParameter("userId");
        userService.save(userId, userForm.getName(), userForm.getDescription());
        return mapping.findForward("success");
    }
}
