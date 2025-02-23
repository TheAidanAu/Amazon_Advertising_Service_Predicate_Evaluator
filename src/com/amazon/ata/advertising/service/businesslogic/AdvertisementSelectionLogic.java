package com.amazon.ata.advertising.service.businesslogic;

import com.amazon.ata.advertising.service.dao.ReadableDao;
import com.amazon.ata.advertising.service.dao.TargetingGroupDao;
import com.amazon.ata.advertising.service.model.AdvertisementContent;
import com.amazon.ata.advertising.service.model.EmptyGeneratedAdvertisement;
import com.amazon.ata.advertising.service.model.GeneratedAdvertisement;
import com.amazon.ata.advertising.service.model.RequestContext;
import com.amazon.ata.advertising.service.targeting.TargetingEvaluator;
import com.amazon.ata.advertising.service.targeting.TargetingGroup;

import com.amazon.ata.advertising.service.targeting.predicate.TargetingPredicateResult;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;
import javax.inject.Inject;

/**
 * This class is responsible for picking the advertisement to be rendered.
 */
public class AdvertisementSelectionLogic {

    private static final Logger LOG = LogManager.getLogger(AdvertisementSelectionLogic.class);

    private final ReadableDao<String, List<AdvertisementContent>> contentDao;
    private final ReadableDao<String, List<TargetingGroup>> targetingGroupDao;
    private Random random = new Random();

    /**
     * Constructor for AdvertisementSelectionLogic.
     * @param contentDao Source of advertising content.
     * @param targetingGroupDao Source of targeting groups for each advertising content.
     */
    @Inject
    public AdvertisementSelectionLogic(ReadableDao<String, List<AdvertisementContent>> contentDao,
                                       ReadableDao<String, List<TargetingGroup>> targetingGroupDao) {
        this.contentDao = contentDao;
        this.targetingGroupDao = targetingGroupDao;
    }

    /**
     * Setter for Random class.
     * @param random generates random number used to select advertisements.
     */
    public void setRandom(Random random) {
        this.random = random;
    }

    /**
     * Gets all the content and metadata for the marketplace
     * and determines which content can be shown.
     * Returns the eligible content with the highest CTR.
     * If no advertisement is available or eligible,
     * returns an EmptyGeneratedAdvertisement.
     *
     * @param customerId - the customer to generate a custom advertisement for
     * @param marketplaceId - the id of the marketplace the advertisement will be rendered on
     * @return an advertisement customized for the customer id provided, or an empty advertisement if one could
     *     not be generated.
     */
    public GeneratedAdvertisement selectAdvertisement(String customerId, String marketplaceId) {
        GeneratedAdvertisement generatedAdvertisement = new EmptyGeneratedAdvertisement();
        /*
- only select ads that the customer is eligible for based on the ad content's `TargetingGroup`.
- Use streams to filter out ads that the customer is not eligible for
    based on the ad content's `TargetingGroup`.
If there are no eligible ads, return an `EmptyGeneratedAdvertisement`.
         */
        if (StringUtils.isEmpty(marketplaceId)) {
            LOG.warn("MarketplaceId cannot be null or empty. Returning empty ad.");
        } else {
            final List<AdvertisementContent> contents = contentDao.get(marketplaceId);
            // AdvertisementContent attributes:contentId, renderableContent, marketplaceId

            List<String> contentIds = contents.stream()
                    .map(AdvertisementContent::getContentId)
                    .distinct()
                    .collect(Collectors.toList());

            // TargetingGroup attribute: targetingGroupId, contentId, clickThroughRate,
            //                          List<TargetingPredicate> targetingPredicates
            List<TargetingGroup> targetingGroups = new ArrayList<>();
            for (String contentId: contentIds) {
                targetingGroups.addAll(targetingGroupDao.get(contentId));
            }

            // Use TargetingEvaluator class to help filter out
            // the ads that a customer is not eligible for.
            // TargetingEvaluator constructor requires a RequestContext object
            // You can construct a RequestContext object with its required parameters
            TargetingEvaluator targetingEvaluator
                    = new TargetingEvaluator(new RequestContext(customerId, marketplaceId));

            // Filter all the eligible Targeting Groups
            // Then sort it by CTR, then find the first eligible Targeting Group
            Optional<TargetingGroup> firstEligibleTargetingGroup = targetingGroups.stream()
                    .filter(targetingGroup -> targetingEvaluator.evaluate(targetingGroup).equals(TargetingPredicateResult.TRUE))
                    .sorted(Comparator.comparing(TargetingGroup::getClickThroughRate).reversed())
                    .findFirst();

            // Once you get a Targeting Group, you can try to get an ad via its contentId
            // This ad has the highest CTR which is also eligible to show to the customer (if any).
            // Since it's an Optional, you need to check the wrapper box before opening it,
            // otherwise there'll be an exception
            if (firstEligibleTargetingGroup.isPresent()) {
                // Get the contentId of this Targeting Group
                String contentId = firstEligibleTargetingGroup.get().getContentId();
                for (AdvertisementContent content: contents) {
                    if (content.getContentId().equals(contentId)) {
                        // GeneratedAdvertisement is the return type of this method
                        // and its constructor takes an ad content parameter
                        return new GeneratedAdvertisement(content);
                    }
                }

            }
        }

//old code
            //            if (CollectionUtils.isNotEmpty(contents)) {
//                AdvertisementContent randomAdvertisementContent = contents.get(random.nextInt(contents.size()));
//                generatedAdvertisement = new GeneratedAdvertisement(randomAdvertisementContent);
//            }

        return generatedAdvertisement;
    }
}
