db = db.getSiblingDB('smartcart');

db.products.insertMany([
    {
        _id: "prod-001",
        name: "Laptop Dell XPS 15",
        description: "Laptop profesional 15 pulgadas, 32GB RAM, 1TB SSD",
        unitPrice: NumberDecimal("1299.99"),
        stock: 10
    },
    {
        _id: "prod-002",
        name: "Monitor LG 27\"",
        description: "Monitor 4K UHD 27 pulgadas, 60Hz",
        unitPrice: NumberDecimal("399.99"),
        stock: 15
    },
    {
        _id: "prod-003",
        name: "Teclado Mecánico Keychron K2",
        description: "Teclado mecánico inalámbrico, switches Brown",
        unitPrice: NumberDecimal("89.99"),
        stock: 30
    },
    {
        _id: "prod-004",
        name: "Mouse Logitech MX Master 3",
        description: "Mouse inalámbrico ergonómico para productividad",
        unitPrice: NumberDecimal("79.99"),
        stock: 25
    },
    {
        _id: "prod-005",
        name: "Headset Sony WH-1000XM5",
        description: "Audífonos inalámbricos con cancelación de ruido",
        unitPrice: NumberDecimal("349.99"),
        stock: 0  // sin stock — para probar el flujo de OutOfStock
    }
]);

print("✅ Productos insertados correctamente");
db.products.find().forEach(printjson);