package com.fg.auth.model;

import jakarta.persistence.*;


@Entity
@Table(name = "roles")
public class RoleEntity {
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Integer id;


@Column(unique = true, nullable = false)
private String name; // e.g. ROLE_ADMIN



//getters y setters
public Integer getId() {
	return id;
}


public void setId(Integer id) {
	this.id = id;
}


public String getName() {
	return name;
}


public void setName(String name) {
	this.name = name;
}


public RoleEntity(Integer id, String name) {
	super();
	this.id = id;
	this.name = name;
}


public RoleEntity() {
	super();
	// TODO Auto-generated constructor stub
}





}
