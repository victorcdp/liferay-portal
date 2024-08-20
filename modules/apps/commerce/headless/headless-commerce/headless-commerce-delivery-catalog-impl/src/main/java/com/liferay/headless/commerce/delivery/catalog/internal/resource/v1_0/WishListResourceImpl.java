/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.headless.commerce.delivery.catalog.internal.resource.v1_0;

import com.liferay.account.exception.NoSuchEntryException;
import com.liferay.account.model.AccountEntry;
import com.liferay.account.service.AccountEntryLocalService;
import com.liferay.commerce.product.exception.NoSuchChannelException;
import com.liferay.commerce.product.model.CPInstance;
import com.liferay.commerce.product.model.CommerceChannel;
import com.liferay.commerce.product.service.CPInstanceLocalService;
import com.liferay.commerce.product.service.CommerceChannelLocalService;
import com.liferay.commerce.util.CommerceAccountHelper;
import com.liferay.commerce.wish.list.model.CommerceWishList;
import com.liferay.commerce.wish.list.service.CommerceWishListItemService;
import com.liferay.commerce.wish.list.service.CommerceWishListService;
import com.liferay.headless.commerce.core.util.ServiceContextHelper;
import com.liferay.headless.commerce.delivery.catalog.dto.v1_0.WishList;
import com.liferay.headless.commerce.delivery.catalog.dto.v1_0.WishListItem;
import com.liferay.headless.commerce.delivery.catalog.resource.v1_0.WishListResource;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.vulcan.dto.converter.DTOConverter;
import com.liferay.portal.vulcan.dto.converter.DTOConverterRegistry;
import com.liferay.portal.vulcan.dto.converter.DefaultDTOConverterContext;
import com.liferay.portal.vulcan.pagination.Page;
import com.liferay.portal.vulcan.pagination.Pagination;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ServiceScope;

/**
 * @author Mahmoud Azzam
 */
@Component(
	properties = "OSGI-INF/liferay/rest/v1_0/wish-list.properties",
	scope = ServiceScope.PROTOTYPE, service = WishListResource.class
)
public class WishListResourceImpl extends BaseWishListResourceImpl {

	@Override
	public void deleteWishList(Long wishListId) throws Exception {
		_commerceWishListService.deleteCommerceWishList(wishListId);
	}

	@Override
	public Page<WishList> getChannelByExternalReferenceCodeWishListsPage(
			String externalReferenceCode, Long accountId, Pagination pagination)
		throws Exception {

		CommerceChannel commerceChannel =
			_commerceChannelLocalService.
				getCommerceChannelByExternalReferenceCode(
					externalReferenceCode, contextCompany.getCompanyId());

		return getChannelWishListsPage(
			commerceChannel.getCommerceChannelId(), accountId, pagination);
	}

	@Override
	public Page<WishList> getChannelWishListsPage(
			Long channelId, Long accountId, Pagination pagination)
		throws Exception {

		CommerceChannel commerceChannel =
			_commerceChannelLocalService.fetchCommerceChannel(channelId);

		if (commerceChannel == null) {
			throw new NoSuchChannelException();
		}

		return Page.of(
			transform(
				_commerceWishListService.getCommerceWishLists(
					commerceChannel.getSiteGroupId(), contextUser.getUserId(),
					pagination.getStartPosition(), pagination.getEndPosition(),
					null),
				commerceWishList -> _toWishList(commerceWishList)),
			pagination,
			_commerceWishListService.getCommerceWishListsCount(
				commerceChannel.getSiteGroupId(), contextUser.getUserId()));
	}

	@Override
	public WishList getWishList(Long wishListId) throws Exception {
		return _toWishList(
			_commerceWishListService.getCommerceWishList(wishListId));
	}

	@Override
	public WishList patchWishList(Long wishListId, WishList wishList)
		throws Exception {

		CommerceWishList commerceWishList =
			_commerceWishListService.getCommerceWishList(wishListId);

		return _toWishList(
			_commerceWishListService.updateCommerceWishList(
				wishListId,
				GetterUtil.getString(
					wishList.getName(), commerceWishList.getName()),
				GetterUtil.getBoolean(
					wishList.getDefaultWishList(),
					commerceWishList.isDefaultWishList())));
	}

	@Override
	public WishList postChannelByExternalReferenceCodeWishList(
			String externalReferenceCode, Long accountId, WishList wishList)
		throws Exception {

		CommerceChannel commerceChannel =
			_commerceChannelLocalService.
				getCommerceChannelByExternalReferenceCode(
					externalReferenceCode, contextCompany.getCompanyId());

		return postChannelWishList(
			commerceChannel.getCommerceChannelId(), accountId, wishList);
	}

	@Override
	public WishList postChannelWishList(
			Long channelId, Long accountId, WishList wishList)
		throws Exception {

		CommerceChannel commerceChannel =
			_commerceChannelLocalService.getCommerceChannel(channelId);

		CommerceWishList commerceWishList =
			_commerceWishListService.addCommerceWishList(
				GetterUtil.getString(wishList.getName()),
				GetterUtil.getBoolean(wishList.getDefaultWishList()),
				_serviceContextHelper.getServiceContext(
					commerceChannel.getSiteGroupId()));

		_updateNestedResources(commerceWishList, wishList, accountId);

		return _toWishList(commerceWishList);
	}

	private Long _getCommerceAccountId(
			Long accountId, CommerceChannel commerceChannel)
		throws Exception {

		int countUserCommerceAccounts =
			_commerceAccountHelper.countUserCommerceAccounts(
				contextUser.getUserId(), commerceChannel.getGroupId());

		if (countUserCommerceAccounts > 1) {
			if (accountId == null) {
				throw new NoSuchEntryException();
			}
		}
		else {
			long[] commerceAccountIds =
				_commerceAccountHelper.getUserCommerceAccountIds(
					contextUser.getUserId(), commerceChannel.getGroupId());

			if (commerceAccountIds.length == 0) {
				AccountEntry accountEntry =
					_accountEntryLocalService.getGuestAccountEntry(
						contextCompany.getCompanyId());

				commerceAccountIds = new long[] {
					accountEntry.getAccountEntryId()
				};
			}

			return commerceAccountIds[0];
		}

		return accountId;
	}

	private WishList _toWishList(CommerceWishList commerceWishList)
		throws Exception {

		return _wishListDTOConverter.toDTO(
			new DefaultDTOConverterContext(
				_dtoConverterRegistry, commerceWishList.getCommerceWishListId(),
				contextAcceptLanguage.getPreferredLocale(), contextUriInfo,
				contextUser));
	}

	private void _updateNestedResources(
			CommerceWishList commerceWishList, WishList wishList,
			Long accountId)
		throws Exception {

		WishListItem[] wishListItems = wishList.getWishListItems();

		if (wishListItems != null) {
			for (WishListItem wishListItem : wishListItems) {
				CPInstance cpInstance = _cpInstanceLocalService.getCPInstance(
					GetterUtil.getLong(wishListItem.getSkuId()));

				String cpInstanceUuid = StringPool.BLANK;

				if (cpInstance != null) {
					cpInstanceUuid = cpInstance.getCPInstanceUuid();
				}

				CommerceChannel commerceChannel =
					_commerceChannelLocalService.
						fetchCommerceChannelBySiteGroupId(
							commerceWishList.getGroupId());

				if (commerceChannel == null) {
					throw new NoSuchChannelException();
				}

				_commerceWishListItemService.addCommerceWishListItem(
					_getCommerceAccountId(accountId, commerceChannel),
					commerceWishList.getCommerceWishListId(),
					wishListItem.getProductId(), cpInstanceUuid,
					wishListItem.toString(),
					_serviceContextHelper.getServiceContext(
						commerceChannel.getSiteGroupId()));
			}
		}
	}

	@Reference
	private AccountEntryLocalService _accountEntryLocalService;

	@Reference
	private CommerceAccountHelper _commerceAccountHelper;

	@Reference
	private CommerceChannelLocalService _commerceChannelLocalService;

	@Reference
	private CommerceWishListItemService _commerceWishListItemService;

	@Reference
	private CommerceWishListService _commerceWishListService;

	@Reference
	private CPInstanceLocalService _cpInstanceLocalService;

	@Reference
	private DTOConverterRegistry _dtoConverterRegistry;

	@Reference
	private ServiceContextHelper _serviceContextHelper;

	@Reference(
		target = "(component.name=com.liferay.headless.commerce.delivery.catalog.internal.dto.v1_0.converter.WishListDTOConverter)"
	)
	private DTOConverter<CommerceWishList, WishList> _wishListDTOConverter;

}