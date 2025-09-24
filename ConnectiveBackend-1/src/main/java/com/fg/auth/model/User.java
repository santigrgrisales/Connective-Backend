package com.fg.auth.model;

import jakarta.persistence.*;
import java.util.Set;


@Entity
@Table(name = "users")
public class User {
@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;


@Column(nullable = false, unique = true)
private String email;


@Column(nullable = false)
private String password;


private String fullName;


private boolean enabled = true;


@ManyToMany(fetch = FetchType.EAGER)
@JoinTable(name = "users_roles",
joinColumns = @JoinColumn(name = "user_id"),
inverseJoinColumns = @JoinColumn(name = "role_id"))
private Set<RoleEntity> roles;


public Long getId() {
	return id;
}


public void setId(Long id) {
	this.id = id;
}


public String getEmail() {
	return email;
}


public void setEmail(String email) {
	this.email = email;
}


public String getPassword() {
	return password;
}


public void setPassword(String password) {
	this.password = password;
}


public String getFullName() {
	return fullName;
}


public void setFullName(String fullName) {
	this.fullName = fullName;
}


public boolean isEnabled() {
	return enabled;
}


public void setEnabled(boolean enabled) {
	this.enabled = enabled;
}


public Set<RoleEntity> getRoles() {
	return roles;
}


public void setRoles(Set<RoleEntity> roles) {
	this.roles = roles;
}


public User(Long id, String email, String password, String fullName, boolean enabled, Set<RoleEntity> roles) {
	super();
	this.id = id;
	this.email = email;
	this.password = password;
	this.fullName = fullName;
	this.enabled = enabled;
	this.roles = roles;
}


public User() {
	super();
	// TODO Auto-generated constructor stub
}


// getters y setters


}