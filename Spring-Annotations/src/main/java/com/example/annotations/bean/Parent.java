package com.example.annotations.bean;

public class Parent implements GrandParent {

	@Override
	public void profession(String text) {
		System.out.println(text);
		
	}

}
