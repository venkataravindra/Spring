package com.example.annotations.bean;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/*@Qualifier("children")*/

public class Children implements GrandParent{

	@Override
	public void profession(String text) {
		System.out.println(text);
		
	}

}
