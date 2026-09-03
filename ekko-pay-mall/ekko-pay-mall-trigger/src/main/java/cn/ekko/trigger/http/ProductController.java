package cn.ekko.trigger.http;

import cn.ekko.api.IProductApi;
import cn.ekko.api.dto.QueryProductResponseDTO;
import cn.ekko.api.response.Response;
import cn.ekko.domain.order.model.entity.GroupBuyMarketEntity;
import cn.ekko.domain.order.model.entity.ProductEntity;
import cn.ekko.domain.product.service.IProductService;
import cn.ekko.types.enums.ResponseCode;
import cn.ekko.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/products")
public class ProductController implements IProductApi {

    private final IProductService productService;

    public ProductController(IProductService productService) {
        this.productService = productService;
    }

    @Override
    @GetMapping
    public Response<List<QueryProductResponseDTO>> queryProductList() {
        try {
            List<QueryProductResponseDTO> products = productService.queryProductList().stream()
                    .map(this::toResponseDTO)
                    .toList();
            return Response.<List<QueryProductResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(products)
                    .build();
        } catch (Exception e) {
            log.error("查询商城商品列表失败", e);
            return Response.<List<QueryProductResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @Override
    @GetMapping("/{productId}")
    public Response<QueryProductResponseDTO> queryProductDetail(
            @PathVariable String productId,
            @RequestParam(value = "userId", required = false) String userId) {
        try {
            ProductEntity product = productService.queryProductDetail(productId, userId);
            return Response.<QueryProductResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(toResponseDTO(product))
                    .build();
        } catch (AppException e) {
            log.info("查询商城商品详情失败 productId:{} code:{}", productId, e.getCode());
            return Response.<QueryProductResponseDTO>builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .build();
        } catch (Exception e) {
            log.error("查询商城商品详情异常 productId:{}", productId, e);
            return Response.<QueryProductResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    private QueryProductResponseDTO toResponseDTO(ProductEntity product) {
        return QueryProductResponseDTO.builder()
                .productId(product.getProductId())
                .productName(product.getProductName())
                .productDesc(product.getProductDesc())
                .productModel(product.getProductModel())
                .productSpecs(product.getProductSpecs())
                .basePrice(product.getPrice())
                .imageUrl(product.getImageUrl())
                .groupBuyMarket(toMarketDTO(product.getGroupBuyMarket()))
                .build();
    }

    private QueryProductResponseDTO.GroupBuyMarketDTO toMarketDTO(GroupBuyMarketEntity market) {
        if (null == market) return null;
        List<QueryProductResponseDTO.TeamDTO> teams = null == market.getTeamList()
                ? List.of()
                : market.getTeamList().stream().map(this::toTeamDTO).toList();
        return QueryProductResponseDTO.GroupBuyMarketDTO.builder()
                .activityId(market.getActivityId())
                .originalPrice(market.getOriginalPrice())
                .deductionPrice(market.getDeductionPrice())
                .payPrice(market.getPayPrice())
                .teamList(teams)
                .teamStatistic(toTeamStatisticDTO(market.getTeamStatistic()))
                .build();
    }

    private QueryProductResponseDTO.TeamDTO toTeamDTO(GroupBuyMarketEntity.TeamEntity team) {
        return QueryProductResponseDTO.TeamDTO.builder()
                .userId(team.getUserId())
                .teamId(team.getTeamId())
                .activityId(team.getActivityId())
                .targetCount(team.getTargetCount())
                .completeCount(team.getCompleteCount())
                .lockCount(team.getLockCount())
                .validStartTime(team.getValidStartTime())
                .validEndTime(team.getValidEndTime())
                .validTimeCountdown(team.getValidTimeCountdown())
                .outTradeNo(team.getOutTradeNo())
                .build();
    }

    private QueryProductResponseDTO.TeamStatisticDTO toTeamStatisticDTO(
            GroupBuyMarketEntity.TeamStatisticEntity statistic) {
        if (null == statistic) return null;
        return QueryProductResponseDTO.TeamStatisticDTO.builder()
                .allTeamCount(statistic.getAllTeamCount())
                .allTeamCompleteCount(statistic.getAllTeamCompleteCount())
                .allTeamUserCount(statistic.getAllTeamUserCount())
                .build();
    }
}
