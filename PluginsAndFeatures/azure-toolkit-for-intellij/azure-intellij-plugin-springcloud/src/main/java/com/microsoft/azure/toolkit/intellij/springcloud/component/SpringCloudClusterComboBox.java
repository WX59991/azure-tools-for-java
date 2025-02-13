/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.intellij.springcloud.component;

import com.microsoft.azure.toolkit.intellij.common.AzureComboBox;
import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.common.cache.CacheManager;
import com.microsoft.azure.toolkit.lib.common.model.Subscription;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import com.microsoft.azure.toolkit.lib.springcloud.AzureSpringCloud;
import com.microsoft.azure.toolkit.lib.springcloud.SpringCloudCluster;
import com.microsoft.azure.toolkit.lib.springcloud.SpringCloudClusterModule;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class SpringCloudClusterComboBox extends AzureComboBox<SpringCloudCluster> {

    private Subscription subscription;

    @Override
    protected String getItemText(final Object item) {
        if (Objects.isNull(item)) {
            return AzureComboBox.EMPTY_ITEM;
        }
        return ((SpringCloudCluster) item).name();
    }

    public void setSubscription(Subscription subscription) {
        if (Objects.equals(subscription, this.subscription)) {
            return;
        }
        this.subscription = subscription;
        if (subscription == null) {
            this.clear();
            return;
        }
        this.reloadItems();
    }

    @Nullable
    @Override
    protected SpringCloudCluster doGetDefaultValue() {
        return CacheManager.getUsageHistory(SpringCloudCluster.class)
            .peek(v -> Objects.isNull(subscription) || Objects.equals(subscription.getId(), v.getSubscriptionId()));
    }

    @NotNull
    @Override
    @AzureOperation(
        name = "springcloud.list_clusters.subscription",
        params = {"this.subscription.getId()"},
        type = AzureOperation.Type.SERVICE
    )
    protected List<? extends SpringCloudCluster> loadItems() {
        if (Objects.nonNull(this.subscription)) {
            final String sid = this.subscription.getId();
            final SpringCloudClusterModule az = Azure.az(AzureSpringCloud.class).clusters(sid);
            return az.list();
        }
        return Collections.emptyList();
    }

    @Override
    protected void refreshItems() {
        Optional.ofNullable(this.subscription).ifPresent(s -> Azure.az(AzureSpringCloud.class).clusters(s.getId()).refresh());
        super.refreshItems();
    }
}
