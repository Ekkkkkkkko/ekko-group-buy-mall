package cn.ekko.types.design.framework.link.model1;

/**
 * 责任链节点接口。
 */
public interface ILogicLink<T, D, R> extends ILogicChainArmory<T, D, R> {

    R apply(T requestParameter, D dynamicContext) throws Exception;

}
