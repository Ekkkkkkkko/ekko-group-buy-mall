package cn.ekko.types.design.framework.link.model2.handler;

/**
 * 业务链节点处理器。
 */
public interface ILogicHandler<T, D, R> {

    default R next(T requestParameter, D dynamicContext) {
        return null;
    }

    R apply(T requestParameter, D dynamicContext) throws Exception;

}
