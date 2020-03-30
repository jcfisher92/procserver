package com.emerywaterhouse.bean;

import java.util.Date;

public class DuplicateOrder {

	private int orderId;
	private Date orderEnteredDate;
	private int existingOrderId;
	private Date existingOrderEnteredDate;
	private String customerName;
	private String customerId;
	private String purchaseOrderNumber; 

	public DuplicateOrder(int orderId,  Date orderEnteredDate, 
			int existingOrderId, Date existingOrderEnteredDate,
			String customerName, String customerId, String purchaseOrderNumber) {
		
		this.orderId = orderId;
		this.orderEnteredDate = orderEnteredDate;
		this.existingOrderId = existingOrderId;
		this.existingOrderEnteredDate = existingOrderEnteredDate;
		this.customerName = customerName;
		this.customerId = customerId;		
		this.purchaseOrderNumber = purchaseOrderNumber;
	}

	public int getOrderId() {
		return orderId;
	}

	public void setOrderId(int orderId) {
		this.orderId = orderId;
	}

	public Date getOrderEnteredDate() {
		return orderEnteredDate;
	}

	public void setOrderEnteredDate(Date orderEnteredDate) {
		this.orderEnteredDate = orderEnteredDate;
	}

	public int getExistingOrderId() {
		return existingOrderId;
	}

	public void setExistingOrderId(int existingOrderId) {
		this.existingOrderId = existingOrderId;
	}

	public Date getExistingOrderEnteredDate() {
		return existingOrderEnteredDate;
	}

	public void setExistingOrderEnteredDate(Date existingOrderEnteredDate) {
		this.existingOrderEnteredDate = existingOrderEnteredDate;
	}

	public String getCustomerName() {
		return customerName;
	}

	public void setCustomerName(String customerName) {
		this.customerName = customerName;
	}

	public String getCustomerId() {
		return customerId;
	}

	public void setCustomerId(String customerId) {
		this.customerId = customerId;
	}

	public String getPurchaseOrderNumber() {
		return purchaseOrderNumber;
	}

	public void setPurchaseOrderNumber(String purchaseOrderNumber) {
		this.purchaseOrderNumber = purchaseOrderNumber;
	}
}
