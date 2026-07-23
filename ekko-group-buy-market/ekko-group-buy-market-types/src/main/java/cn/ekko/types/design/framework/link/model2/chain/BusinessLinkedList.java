package cn.ekko.types.design.framework.link.model2.chain;

import cn.ekko.types.design.framework.link.model2.handler.ILogicHandler;

/**
 * 顺序执行的业务责任链。
 */
public class BusinessLinkedList<T, D, R> extends LinkedList<ILogicHandler<T, D, R>> implements ILogicHandler<T, D, R> {

    public BusinessLinkedList(String name) {
        super(name);
    }

    @Override
    public R apply(T requestParameter, D dynamicContext) throws Exception {
        Node<ILogicHandler<T, D, R>> current = this.first;
        while (current != null) {
            ILogicHandler<T, D, R> item = current.item;
            R apply = item.apply(requestParameter, dynamicContext);
            if (apply != null) {
                return apply;
            }
            current = current.next;
        }
        return null;
    }

}
