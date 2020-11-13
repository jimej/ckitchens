package com.proj.ckitchens.utils;

public class DoublyLinkedNode {
    private DoublyLinkedNode previous;
    private DoublyLinkedNode next;
    private final int value;
    public DoublyLinkedNode(int value) {
        this.value = value;
    }

    public void setPrevious(DoublyLinkedNode node) {
        this.previous = node;
    }

    public DoublyLinkedNode getPrevious() {
        return previous;
    }

    public void setNext(DoublyLinkedNode node) {
        this.next = node;
    }

    public DoublyLinkedNode getNext() {
        return next;
    }

    public int value() {
        return this.value;
    }

//    public String toString() {
//        return "value: " + this.value + " prev: " + previous + " next: " + next;
//    }

}
