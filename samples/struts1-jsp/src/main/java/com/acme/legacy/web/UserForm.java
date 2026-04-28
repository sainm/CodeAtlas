package com.acme.legacy.web;

import org.apache.struts.action.ActionForm;

public class UserForm extends ActionForm {
    private String userId;
    private String name;
    private String description;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
