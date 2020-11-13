package com.proj.ckitchens.common;

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

    public DoublyLinkedNode previous() {
        return previous;
    }

    public void setNext(DoublyLinkedNode node) {
        this.next = node;
    }

    public DoublyLinkedNode next() {
        return next;
    }

    public int value() {
        return this.value;
    }
}
