using System;
using System.Collections.Generic;
using System.Linq;
using System.Text.Json;
using System.Threading.Tasks;
using StackExchange.Redis;

namespace Hipstershop
{
    public class Cart
    {
        public Cart()
        {
            Items = new List<CartItem>();
        }

        public string UserId { get; set; }
        public List<CartItem> Items { get; set; }
    }

    public class CartItem
    {
        public string ProductId { get; set; }
        public int Quantity { get; set; }
    }
}

namespace AspNetCoreWebApi
{
    public class Redis
    {
        private const string CART_FIELD_NAME = "cart";
        private const int REDIS_RETRY_NUM = 5;

        public ConnectionMultiplexer ConnectionMultiplexer => redis;
        private volatile ConnectionMultiplexer redis;
        private volatile bool isRedisConnectionOpened = false;

        private readonly object locker = new object();
        private readonly string emptyCartBytes;
        private readonly string connectionString;

        private readonly ConfigurationOptions redisConnectionOptions;

        public Redis(string redisAddress)
        {
            // Serialize empty cart into byte array.
            var cart = new Hipstershop.Cart();
            emptyCartBytes = JsonSerializer.Serialize(cart);
            connectionString = $"{redisAddress},ssl=false,allowAdmin=true,connectRetry=5";

            redisConnectionOptions = ConfigurationOptions.Parse(connectionString);

            // Try to reconnect if first retry failed (up to 5 times with exponential backoff)
            redisConnectionOptions.ConnectRetry = REDIS_RETRY_NUM;
            redisConnectionOptions.ReconnectRetryPolicy = new ExponentialRetry(100);

            redisConnectionOptions.KeepAlive = 180;
        }

        public Task InitializeAsync()
        {
            EnsureRedisConnected();
            return Task.CompletedTask;
        }

        private void EnsureRedisConnected()
        {
            if (isRedisConnectionOpened)
            {
                return;
            }

            // Connection is closed or failed - open a new one but only at the first thread
            lock (locker)
            {
                if (isRedisConnectionOpened)
                {
                    return;
                }

                Console.WriteLine("Connecting to Redis: " + connectionString);
                redis = ConnectionMultiplexer.Connect(redisConnectionOptions);

                if (redis == null || !redis.IsConnected)
                {
                    Console.WriteLine("Wasn't able to connect to redis");

                    // We weren't able to connect to redis despite 5 retries with exponential backoff
                    throw new ApplicationException("Wasn't able to connect to redis");
                }

                Console.WriteLine("Successfully connected to Redis");
                var cache = redis.GetDatabase();

                Console.WriteLine("Performing small test");
                cache.StringSet("cart", "OK" );
                object res = cache.StringGet("cart");
                Console.WriteLine($"Small test result: {res}");

                redis.InternalError += (o, e) => { Console.WriteLine(e.Exception); };
                redis.ConnectionRestored += (o, e) =>
                {
                    isRedisConnectionOpened = true;
                    Console.WriteLine("Connection to redis was retored successfully");
                };
                redis.ConnectionFailed += (o, e) =>
                {
                    Console.WriteLine("Connection failed. Disposing the object");
                    isRedisConnectionOpened = false;
                };

                isRedisConnectionOpened = true;
            }
        }

        public async Task AddItemAsync(string userId, string productId, int quantity)
        {
            Console.WriteLine($"AddItemAsync called with userId={userId}, productId={productId}, quantity={quantity}");
            EnsureRedisConnected();
            var db = redis.GetDatabase();
            var value = await db.HashGetAsync(userId, CART_FIELD_NAME);

            Hipstershop.Cart cart;
            if (value.IsNull)
            {
                cart = new Hipstershop.Cart();
                cart.UserId = userId;
                cart.Items.Add(new Hipstershop.CartItem { ProductId = productId, Quantity = quantity });
            }
            else
            {
                cart = JsonSerializer.Deserialize<Hipstershop.Cart>(value);
                var existingItem = cart.Items.SingleOrDefault(i => i.ProductId == productId);
                if (existingItem == null)
                {
                    cart.Items.Add(new Hipstershop.CartItem { ProductId = productId, Quantity = quantity });
                }
                else
                {
                    existingItem.Quantity += quantity;
                }
            }

            await db.HashSetAsync(userId, new[] { new HashEntry(CART_FIELD_NAME, JsonSerializer.Serialize(cart)) });
        }

        public async Task EmptyCartAsync(string userId)
        {
            Console.WriteLine($"EmptyCartAsync called with userId={userId}");
            EnsureRedisConnected();
            var db = redis.GetDatabase();
            await db.HashSetAsync(userId, new[] { new HashEntry(CART_FIELD_NAME, emptyCartBytes) });
        }

        public async Task<Hipstershop.Cart> GetCartAsync(string userId)
        {
            Console.WriteLine($"GetCartAsync called with userId={userId}");
            EnsureRedisConnected();
            var db = redis.GetDatabase();
            var value = await db.HashGetAsync(userId, CART_FIELD_NAME);
            if (!value.IsNull)
            {
                return JsonSerializer.Deserialize<Hipstershop.Cart>(value);
            }
            return new Hipstershop.Cart();
        }

        public bool Ping()
        {
            try
            {
                var cache = redis.GetDatabase();
                var res = cache.Ping();
                return res != TimeSpan.Zero;
            }
            catch (Exception)
            {
                return false;
            }
        }
    }
}
