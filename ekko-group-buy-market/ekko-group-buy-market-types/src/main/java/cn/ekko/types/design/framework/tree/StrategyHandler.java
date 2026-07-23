package cn.ekko.types.design.framework.tree;

/**
 * 策略处理器。
 */
@FunctionalInterface
public interface StrategyHandler<T, D, R> {

    @SuppressWarnings("rawtypes")
    StrategyHandler DEFAULT = (requestParameter, dynamicContext) -> null;

    R apply(T requestParameter, D dynamicContext) throws Exception;

}
