package com.example.annotations.bean;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class FamilyDetail {

/*	
 * 
 * autowiring byType
 * private Parent parent;
	private Children children;
	
	public void setParent(Parent parent) {
		this.parent = parent;
	}
	public void setChildren( Children children) {
		this.children = children;
	}*/
	/* autowiring byName*/
	//@Autowired
	private Parent parent;
	//@Autowired
	private Children children;
	/*@Autowired
	public FamilyDetail(Parent parent) {
		super();
		this.parent = parent;
		
	}*/
	/*Autowiring by type*/
	/*@Autowired
	@Qualifier("toconsole")*/
	@Inject
	@Named("parent")
	public void setParent(Parent parent) {
		this.parent = parent;
	}
	/*@Autowired
	@Qualifier("children")*/
	@Inject
	@Named("children")
	public void setChildren(Children children) {
		this.children = children;
	}
	public void parentJob(String text)
	{
		parent.profession(text);
	}
	public void childrenJob(String text)
	{
		children.profession(text);
	}
	@PostConstruct
	public void init() {
		System.out.println("init");
	}
	@PreDestroy
	public void destroy()
	{
		System.out.println("destroy");
	}
}


