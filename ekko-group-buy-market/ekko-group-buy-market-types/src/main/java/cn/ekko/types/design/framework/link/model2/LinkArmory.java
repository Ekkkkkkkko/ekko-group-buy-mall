package cn.ekko.types.design.framework.link.model2;

import cn.ekko.types.design.framework.link.model2.chain.BusinessLinkedList;
import cn.ekko.types.design.framework.link.model2.handler.ILogicHandler;

/**
 * 业务链装配器。
 */
public class LinkArmory<T, D, R> {

    private final BusinessLinkedList<T, D, R> logicLink;

    @SafeVarargs
    public LinkArmory(String linkName, ILogicHandler<T, D, R>... logicHandlers) {
        logicLink = new BusinessLinkedList<>(linkName);
        for (ILogicHandler<T, D, R> logicHandler : logicHandlers) {
            logicLink.add(logicHandler);
        }
    }

    public BusinessLinkedList<T, D, R> getLogicLink() {
        return logicLink;
    }

}
