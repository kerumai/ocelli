package rx.loadbalancer.selector;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.loadbalancer.HostEvent;
import rx.loadbalancer.LoadBalancer;
import rx.loadbalancer.ManagedClientFactory;
import rx.loadbalancer.PartitionedLoadBalancer;
import rx.loadbalancer.client.Behaviors;
import rx.loadbalancer.client.Connects;
import rx.loadbalancer.client.TestClient;
import rx.loadbalancer.client.TestClientFactory;
import rx.loadbalancer.client.TestClientMetrics;
import rx.loadbalancer.client.TestClientMetricsFactory;
import rx.loadbalancer.client.TestHost;
import rx.loadbalancer.loadbalancer.DefaultLoadBalancer;
import rx.loadbalancer.util.RxUtil;
import rx.subjects.PublishSubject;

import com.google.common.collect.Sets;

public class PartitionedLoadBalancerTest {
    private static final Logger LOG = LoggerFactory.getLogger(PartitionedLoadBalancerTest.class);
    
    private ManagedClientFactory<TestHost,TestClient,TestClientMetrics> factory = new ManagedClientFactory<TestHost,TestClient,TestClientMetrics>(
            new TestClientFactory(), 
            new TestClientMetricsFactory<TestHost>());
    
    @Test
    public void testVip() throws InterruptedException {
        PublishSubject<HostEvent<TestHost>> hostSource = PublishSubject.create();
        
        TestHost h1 = TestHost.create("h1", Connects.immediate(), Behaviors.immediate());
        TestHost h2 = TestHost.create("h2", Connects.immediate(), Behaviors.immediate()).withVip("a");
        TestHost h3 = TestHost.create("h3", Connects.immediate(), Behaviors.immediate()).withVip("a").withVip("b");
        TestHost h4 = TestHost.create("h4", Connects.immediate(), Behaviors.immediate()).withVip("b");
        
        DefaultLoadBalancer<TestHost, TestClient, TestClientMetrics> lb = DefaultLoadBalancer.<TestHost, TestClient, TestClientMetrics>builder()
                .withName("core")
                .withHostSource(hostSource)
                .withClientFactory(factory)
                .build()
                ;
        PartitionedLoadBalancer<TestHost, TestClient, String> plb = lb.partition(TestHost.byVip());
        plb.initialize();
        
        lb.initialize();
//        lb.events().subscribe(RxUtil.info(""));

        //////////////////////////
        // Step 1: Add 4 hosts
        
        // Add 4 hosts
        hostSource.onNext(HostEvent.create(h1, HostEvent.EventType.ADD));
        hostSource.onNext(HostEvent.create(h2, HostEvent.EventType.ADD));
        hostSource.onNext(HostEvent.create(h3, HostEvent.EventType.ADD));
        hostSource.onNext(HostEvent.create(h4, HostEvent.EventType.ADD));
        
        TimeUnit.SECONDS.sleep(10);
        
        // Get a LoadBalancer for each partition
        LoadBalancer<TestHost, TestClient> lbA = plb.get("a");
        LoadBalancer<TestHost, TestClient> lbB = plb.get("b");
        LoadBalancer<TestHost, TestClient> lbAll = plb.get("*");
        
        Assert.assertNotNull(lbA);
        Assert.assertNotNull(lbB);
        Assert.assertNotNull(lbAll);
        
        // List all hosts
        Set<TestHost> hostsA = new HashSet<TestHost>(lbA.listAllHosts().toList().toBlocking().first());
        Set<TestHost> hostsB = new HashSet<TestHost>(lbB.listAllHosts().toList().toBlocking().first());
        Set<TestHost> hostsAll = new HashSet<TestHost>(lbAll.listAllHosts().toList().toBlocking().first());

        // Assert initial state
        Assert.assertEquals(Sets.newHashSet(h2, h3), hostsA);
        Assert.assertEquals(Sets.newHashSet(h3, h4), hostsB);
        Assert.assertEquals(Sets.newHashSet(h1,h2,h3, h4), hostsAll);
        
        //////////////////////////
        // Step 2: Remove h1 host
        hostSource.onNext(HostEvent.create(h1, HostEvent.EventType.REMOVE));
        
        // List all hosts
        hostsA = new HashSet<TestHost>(lbA.listAllHosts().toList().toBlocking().first());
        hostsB = new HashSet<TestHost>(lbB.listAllHosts().toList().toBlocking().first());
        hostsAll = new HashSet<TestHost>(lbAll.listAllHosts().toList().toBlocking().first());

        // Assert initial state
        Assert.assertEquals(Sets.newHashSet(h2, h3), hostsA);
        Assert.assertEquals(Sets.newHashSet(h3, h4), hostsB);
        Assert.assertEquals(Sets.newHashSet(h2,h3, h4), hostsAll);
        
        //////////////////////////
        // Step 2: Remove h3 host
        hostSource.onNext(HostEvent.create(h3, HostEvent.EventType.REMOVE));
        
        // List all hosts
        hostsA = new HashSet<TestHost>(lbA.listAllHosts().toList().toBlocking().first());
        hostsB = new HashSet<TestHost>(lbB.listAllHosts().toList().toBlocking().first());
        hostsAll = new HashSet<TestHost>(lbAll.listAllHosts().toList().toBlocking().first());

        // Assert initial state
        Assert.assertEquals(Sets.newHashSet(h2), hostsA);
        Assert.assertEquals(Sets.newHashSet(h4), hostsB);
        Assert.assertEquals(Sets.newHashSet(h2, h4), hostsAll);
        
        // Assert in
        lbA.listAllHosts().subscribe(RxUtil.info("A:"));
        lbB.listAllHosts().subscribe(RxUtil.info("B:"));
        lbAll.listAllHosts().subscribe(RxUtil.info("All:"));
    }
    
    @Test
    public void testVipHostFailure() {
        PublishSubject<HostEvent<TestHost>> hostSource = PublishSubject.create();
        
        TestHost h2 = TestHost.create("h2", Connects.immediate(), Behaviors.immediate()).withVip("a");
        
        DefaultLoadBalancer<TestHost, TestClient, TestClientMetrics> lb = DefaultLoadBalancer.<TestHost, TestClient, TestClientMetrics>builder()
                .withName("core")
                .withHostSource(hostSource)
                .withClientFactory(factory)
                .build()
                ;
        PartitionedLoadBalancer<TestHost, TestClient, String> plb = lb.partition(TestHost.byVip());
        plb.initialize();
        lb.initialize();
        lb.events().subscribe(RxUtil.info(""));

        //////////////////////////
        // Step 1: Add 4 hosts
        
        // Add 4 hosts
        hostSource.onNext(HostEvent.create(h2, HostEvent.EventType.ADD));
        
        // Get a LoadBalancer for each partition
        LoadBalancer<TestHost, TestClient> lbA = plb.get("a");
        LoadBalancer<TestHost, TestClient> lbAll = plb.get("*");
        
        // List all hosts
        Set<TestHost> hostsA = new HashSet<TestHost>(lbA.listAllHosts().toList().toBlocking().first());
        Set<TestHost> hostsAll = new HashSet<TestHost>(lbAll.listAllHosts().toList().toBlocking().first());

        // Assert initial state
        Assert.assertEquals(Sets.newHashSet(h2), hostsA);
        Assert.assertEquals(Sets.newHashSet(h2), hostsAll);
        
        //////////////////////////
        // Step 2: Kill h2 host
        TestClientMetrics m = factory.get(h2).getMetrics();
        Assert.assertNotNull(m);
        m.shutdown();
        
        // List all hosts
        hostsA = new HashSet<TestHost>(lbA.listActiveHosts().toList().toBlocking().first());
        hostsAll = new HashSet<TestHost>(lbAll.listActiveHosts().toList().toBlocking().first());

        // Assert initial state
        Assert.assertTrue(hostsA.isEmpty());
        Assert.assertTrue(hostsAll.isEmpty());
    }
    
    @Test
    public void testShard() {
        
    }
    
    @Test
    public void testConsistentHash() {
        
    }
    
    @Test
    public void testRack() {
        
    }
}