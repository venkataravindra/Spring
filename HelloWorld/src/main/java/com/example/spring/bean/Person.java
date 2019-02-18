package com.example.spring.bean;

public class Person {
	
	int id;
	String name;
	private int taxId;
	private Address address;
	public Person()
	{
		
	}
	public static Person getInstance(int id, String name)
	{
		System.out.println("calling Person using factory method");
	return new Person(id,name);	
	}
	public Person(int id, String name) {
		super();
		this.id = id;
		this.name = name;
		
	}
	public void onCreate()
	{
		System.out.println("Person created: " +this);
	}
	public void onDestroy()
	{
		System.out.println("Person Destroyed");
	}
	public void speak()
	{
		System.out.println("Hello World");
	}
	
	public void setTaxId(int taxId) {
		this.taxId = taxId;
	}
	
	public void setAddress(Address address) {
		this.address = address;
	}
	@Override
	public String toString() {
		return "Person [id=" + id + ", name=" + name + ", taxId=" + taxId + ", address=" + address + "]";
	}
	
	

}
