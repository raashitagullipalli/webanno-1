/*
 * Copyright 2017
 * Ubiquitous Knowledge Processing (UKP) Lab and FG Language Technology
 * Technische Universität Darmstadt
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *  http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.clarin.webanno.ui.core.menu;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Resource;

import org.apache.commons.lang3.reflect.MethodUtils;
import org.apache.wicket.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import de.tudarmstadt.ukp.clarin.webanno.api.RepositoryService;
import de.tudarmstadt.ukp.clarin.webanno.api.UserDao;

public class MenuItemServiceImpl
    implements SmartLifecycle, MenuItemService
{
    private final Logger log = LoggerFactory.getLogger(getClass());

    private boolean running = false;

    private List<MenuItem> menuItems;

    @Resource(name = "documentRepository")
    private RepositoryService repository;

    @Resource(name = "userRepository")
    private UserDao userRepository;
    
    @Override
    public boolean isRunning()
    {
        return running;
    }

    @Override
    public void start()
    {
        running = true;
        scanMenuItems();
    }

    @Override
    public void stop()
    {
        running = false;
    }

    @Override
    public int getPhase()
    {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isAutoStartup()
    {
        return true;
    }

    @Override
    public void stop(Runnable aCallback)
    {
        stop();
        aCallback.run();
    }

    private void scanMenuItems()
    {
        menuItems = new ArrayList<>();

        // Scan menu items from page class annotations
        ClassPathScanningCandidateComponentProvider scanner = 
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(
                de.tudarmstadt.ukp.clarin.webanno.ui.core.menu.MenuItem.class));

        for (BeanDefinition bd : scanner.findCandidateComponents("")) {
            try {
                @SuppressWarnings("unchecked")
                Class<? extends Page> pageClass = (Class<? extends Page>) Class
                        .forName(bd.getBeanClassName());
                de.tudarmstadt.ukp.clarin.webanno.ui.core.menu.MenuItem mia = pageClass
                        .getAnnotation(
                                de.tudarmstadt.ukp.clarin.webanno.ui.core.menu.MenuItem.class);

                MenuItem item = new MenuItem();
                item.icon = mia.icon();
                item.label = mia.label();
                item.prio = mia.prio();
                item.page = pageClass;
                
                List<Method> methods = MethodUtils.getMethodsListWithAnnotation(pageClass,
                        MenuItemCondition.class);
                if (!methods.isEmpty()) {
                    Method m = methods.get(0);
                    item.condition = () -> {
                        try {
                            return (boolean) m.invoke(null, repository, userRepository);
                        }
                        catch (Exception e) {
                            log.error("Unable to invoke menu item condition method", e);
                            return false;
                        }
                    };
                }
                
                menuItems.add(item);
            }
            catch (ClassNotFoundException e) {
                log.error("Menu item class [{}] not found", bd.getBeanClassName(), e);
            }
        }
        
        Collections.sort(menuItems, (a, b) -> { return a.prio - b.prio; });
    }

    @Override
    public List<MenuItemService.MenuItem> getMenuItems()
    {
        return Collections.unmodifiableList(menuItems);
    }
}
