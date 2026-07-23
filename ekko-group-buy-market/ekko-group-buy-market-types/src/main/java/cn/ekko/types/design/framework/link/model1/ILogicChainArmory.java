package cn.ekko.types.design.framework.link.model1;

/**
 * 责任链装配接口。
 */
public interface ILogicChainArmory<T, D, R> {

    ILogicLink<T, D, R> next();

    ILogicLink<T, D, R> appendNext(ILogicLink<T, D, R> next);

}
