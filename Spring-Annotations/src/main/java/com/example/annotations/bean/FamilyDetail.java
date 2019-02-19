package com.example.annotations.bean;

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
	private GrandParent parent;
	private GrandParent children;
	
	public FamilyDetail(Parent parent, Children children) {
		super();
		this.parent = parent;
		this.children = children;
	}
	public void setParent(GrandParent parent) {
		this.parent = parent;
	}
	public void setChildren(GrandParent children) {
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
}


